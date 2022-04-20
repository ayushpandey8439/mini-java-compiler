package translation;

import analysis.ArrayType;
import analysis.ClassType;
import minillvm.ast.*;

import static minillvm.ast.Ast.*;

import notquitejava.ast.*;

import java.util.*;
import java.util.stream.Collectors;

import static frontend.AstPrinter.print;

/**
 * Entry point for the translation phase.
 */
public class Translator {
    private final TranslationTable translationTable = new TranslationTable();
    private final StmtTranslator stmtTranslator = new StmtTranslator(this, translationTable);
    private final ExprLValue exprLValue = new ExprLValue(this, translationTable);
    private final ExprRValue exprRValue = new ExprRValue(this, translationTable);
    private final Map<NQJFunctionDecl, Proc> functionImpl = new HashMap<>();
    private final Prog prog = Ast.Prog(Ast.TypeStructList(), Ast.GlobalList(), Ast.ProcList());
    private final NQJProgram javaProg;

    private final Map<analysis.Type, Type> translatedType = new HashMap<>();
    private final Map<Type, TypeStruct> arrayStruct = new HashMap<>();
    private final Map<Type, Proc> newArrayFuncForType = new HashMap<>();


    final Set<String> handledClasses = new HashSet<>();
    // mutable state
    // Keeps track of the current procedure being visited
    private Proc currentProcedure;
    // keeps track of the current basic block.
    private BasicBlock currentBlock;
    // list of class declarations of AST form.
    private final NQJClassDeclList classDeclList;


    /**
     * Translates the AST Program into llvm.
     */
    public Translator(NQJProgram javaProg) {

        this.classDeclList = javaProg.getClassDecls();
        this.javaProg = javaProg;

    }

    /**
     * Translates given program into a mini llvm program.
     * Begin by creating struct types for class constructors to allow initialisation.
     * Then creates procedures for implicit class constructors.
     * Then creates creates procedures for class methods.
     * And finally, performs scoping of methods and visiting the classes.
     */
    public Prog translate() {

        NQJClassDeclList classDecls = javaProg.getClassDecls();

        classDecls.accept(new ClassStructGenerator(this, prog, translationTable));
        // Initialise the class constructors first to allow references to the class object later.
        classDecls.accept(new ClassConstructorGenerator(this, translationTable));

        //Generate Procs for class methods
        classDecls.accept(new ClassMethodProcGenerator(this, translationTable));

        // translate functions except main
        // has only access to functions
        translateFunctions();

        //Translate methods
        classDecls.accept(new MethodTranslator(this, translationTable));

        // translate main function
        // has access to functions
        translateMainFunction();


        finishNewArrayProcs();

        return prog;
    }


    /**
     * For a constructor, fetch the definititions from the extended classes.
     * This is done during constructor initialization.
     * The references are then added to the Struct of the child class.
     * Recursive fetching of the definitions from the parent until a parent class is present.
     */
    void getDefinitionsFromClassHierarchy(NQJClassDecl classDecl, StructFieldList definitions) {
        if (classDecl.getExtended() instanceof NQJExtendsClass) {
            NQJClassDecl parentClass = getClassFromDeclarationList(
                    ((NQJExtendsClass) classDecl.getExtended()).getName()
            );
            getDefinitionsFromClassHierarchy(parentClass, definitions);
        }
        for (NQJVarDecl field : classDecl.getFields()) {

            Type fieldType = translateType(field.getType());
            //If a field with the same name exists, then we shadow it with the new defition.
            int index = definitions.size();

            definitions.add(index, Ast.StructField(fieldType, field.getName()));

            translationTable.setFieldAddressOffset(field, index);
        }
    }


    private void finishNewArrayProcs() {
        for (Type type : newArrayFuncForType.keySet()) {
            finishNewArrayProc(type);
        }
    }

    private void finishNewArrayProc(Type componentType) {
        final Proc newArrayFunc = newArrayFuncForType.get(componentType);
        final Parameter size = newArrayFunc.getParameters().get(0);

        addProcedure(newArrayFunc);
        setCurrentProc(newArrayFunc);

        BasicBlock init = newBasicBlock("init");
        addBasicBlock(init);
        setCurrentBlock(init);
        TemporaryVar sizeLessThanZero = Ast.TemporaryVar("sizeLessThanZero");
        addInstruction(Ast.BinaryOperation(sizeLessThanZero,
                Ast.VarRef(size), Ast.Slt(), Ast.ConstInt(0)));
        BasicBlock negativeSize = newBasicBlock("negativeSize");
        BasicBlock goodSize = newBasicBlock("goodSize");
        currentBlock.add(Ast.Branch(Ast.VarRef(sizeLessThanZero), negativeSize, goodSize));

        addBasicBlock(negativeSize);
        negativeSize.add(Ast.HaltWithError("Array Size must be positive"));

        addBasicBlock(goodSize);
        setCurrentBlock(goodSize);

        // allocate space for the array

        TemporaryVar arraySizeInBytes = Ast.TemporaryVar("arraySizeInBytes");
        addInstruction(Ast.BinaryOperation(arraySizeInBytes,
                Ast.VarRef(size), Ast.Mul(), byteSize(componentType)));

        // 4 bytes for the length
        TemporaryVar arraySizeWithLen = Ast.TemporaryVar("arraySizeWitLen");
        addInstruction(Ast.BinaryOperation(arraySizeWithLen,
                Ast.VarRef(arraySizeInBytes), Ast.Add(), Ast.ConstInt(4)));

        TemporaryVar mallocResult = Ast.TemporaryVar("mallocRes");
        addInstruction(Ast.Alloc(mallocResult, Ast.VarRef(arraySizeWithLen)));
        TemporaryVar newArray = Ast.TemporaryVar("newArray");
        addInstruction(Bitcast(newArray,
                getArrayPointerType(componentType), Ast.VarRef(mallocResult)));

        // store the size
        TemporaryVar sizeAddr = Ast.TemporaryVar("sizeAddr");
        addInstruction(GetElementPtr(sizeAddr,
                Ast.VarRef(newArray), OperandList(ConstInt(0), ConstInt(0))));
        addInstruction(Store(Ast.VarRef(sizeAddr), Ast.VarRef(size)));

        // initialize Array with zeros:
        final BasicBlock loopStart = newBasicBlock("loopStart");
        final BasicBlock loopBody = newBasicBlock("loopBody");
        final BasicBlock loopEnd = newBasicBlock("loopEnd");
        final TemporaryVar iVar = Ast.TemporaryVar("iVar");
        currentBlock.add(Alloca(iVar, TypeInt()));
        currentBlock.add(Store(Ast.VarRef(iVar), ConstInt(0)));
        currentBlock.add(Jump(loopStart));

        // loop condition: while i < size
        addBasicBlock(loopStart);
        setCurrentBlock(loopStart);
        final TemporaryVar i = Ast.TemporaryVar("i");
        final TemporaryVar nextI = Ast.TemporaryVar("nextI");
        loopStart.add(Load(i, Ast.VarRef(iVar)));
        TemporaryVar smallerSize = Ast.TemporaryVar("smallerSize");
        addInstruction(Ast.BinaryOperation(smallerSize,
                Ast.VarRef(i), Slt(), Ast.VarRef(size)));
        currentBlock.add(Branch(Ast.VarRef(smallerSize), loopBody, loopEnd));

        // loop body
        addBasicBlock(loopBody);
        setCurrentBlock(loopBody);
        // ar[i] = 0;
        final TemporaryVar iAddr = Ast.TemporaryVar("iAddr");
        addInstruction(GetElementPtr(iAddr,
                Ast.VarRef(newArray), OperandList(ConstInt(0), ConstInt(1), Ast.VarRef(i))));
        addInstruction(Store(Ast.VarRef(iAddr), defaultValue(componentType)));

        // nextI = i + 1;
        addInstruction(Ast.BinaryOperation(nextI, Ast.VarRef(i), Add(), ConstInt(1)));
        // store new value in i
        addInstruction(Store(Ast.VarRef(iVar), Ast.VarRef(nextI)));

        loopBody.add(Jump(loopStart));

        addBasicBlock(loopEnd);
        loopEnd.add(ReturnExpr(Ast.VarRef(newArray)));
    }

    private void translateFunctions() {
        for (NQJFunctionDecl functionDecl : javaProg.getFunctionDecls()) {
            if (functionDecl.getName().equals("main")) {
                continue;
            }
            initFunction(functionDecl);
        }
        for (NQJFunctionDecl functionDecl : javaProg.getFunctionDecls()) {
            if (functionDecl.getName().equals("main")) {
                continue;
            }
            translateFunction(functionDecl);
        }
    }

    private void translateMainFunction() {
        NQJFunctionDecl f = null;
        for (NQJFunctionDecl functionDecl : javaProg.getFunctionDecls()) {
            if (functionDecl.getName().equals("main")) {
                f = functionDecl;
                break;
            }
        }

        if (f == null) {
            throw new IllegalStateException("Main function expected");
        }

        Proc proc = Proc("main", TypeInt(), ParameterList(), BasicBlockList());
        addProcedure(proc);
        functionImpl.put(f, proc);

        setCurrentProc(proc);
        BasicBlock initBlock = newBasicBlock("init");
        addBasicBlock(initBlock);
        setCurrentBlock(initBlock);
        translationTable.setCurrentBasicBlockList(proc.getBasicBlocks());
        // allocate space for the local variables
        //allocaLocalVars(f.getMethodBody());

        // translate
        translateStmt(f.getMethodBody());
    }

    private void initFunction(NQJFunctionDecl f) {
        Type returnType = translateType(f.getReturnType());
        ParameterList params = f.getFormalParameters()
                .stream()
                .map(p -> Parameter(translateType(p.getType()), p.getName()))
                .collect(Collectors.toCollection(Ast::ParameterList));
        Proc proc = Proc(f.getName(), returnType, params, BasicBlockList());
        addProcedure(proc);
        functionImpl.put(f, proc);
    }

    private void translateFunction(NQJFunctionDecl m) {
        Proc proc = functionImpl.get(m);
        setCurrentProc(proc);
        BasicBlock initBlock = newBasicBlock("init");
        addBasicBlock(initBlock);
        setCurrentBlock(initBlock);
        translationTable.setCurrentBasicBlockList(proc.getBasicBlocks());

        // store copies of the parameters in Allocas, to make uniform read/write access possible
        int i = 0;
        for (NQJVarDecl param : m.getFormalParameters()) {
            TemporaryVar v = Ast.TemporaryVar(param.getName());
            addInstruction(Alloca(v, translateType(param.getType())));
            addInstruction(Store(Ast.VarRef(v), Ast.VarRef(proc.getParameters().get(i))));
            i++;
        }

        // allocate space for the local variables
        //allocaLocalVars(m.getMethodBody());
        storeFunctionProc(m, proc);
        translateStmt(m.getMethodBody());
    }

    void translateStmt(NQJStatement s) {
        addInstruction(CommentInstr(
                sourceLine(s) + " start statement : " + printFirstline(s)
        ));
        s.match(stmtTranslator);
        addInstruction(CommentInstr(
                sourceLine(s) + " end statement: " + printFirstline(s)
        ));
    }

    int sourceLine(NQJElement e) {
        while (e != null) {
            if (e.getSourcePosition() != null) {
                return e.getSourcePosition().getLine();
            }
            e = e.getParent();
        }
        return 0;
    }

    private String printFirstline(NQJStatement s) {
        String str = print(s);
        str = str.replaceAll("\n.*", "");
        return str;
    }

    BasicBlock newBasicBlock(String name) {
        BasicBlock block = BasicBlock();
        block.setName(name);
        return block;
    }


    Type translateType(NQJType type) {
        return translateType(type.getType());
    }

    Type translateType(analysis.Type t) {
        Type result = translatedType.get(t);
        if (result == null) {
            if (t == analysis.Type.INT) {
                result = TypeInt();
            } else if (t == analysis.Type.BOOL) {
                result = TypeBool();
            } else if (t instanceof ArrayType) {
                ArrayType at = (ArrayType) t;
                result = TypePointer(getArrayStruct(translateType(at.getBaseType())));
            } else if (t instanceof ClassType) {
                ClassType ct = (ClassType) t;
                result = TypePointer(
                        translationTable.getClassTypeStruct(ct.getClassDecl().getName()).get());
            }
            translatedType.put(t, result);
        }
        return result;
    }

    Parameter getThisParameter() {
        // in our case 'this' is always the first parameter
        return currentProcedure.getParameters().get(0);
    }

    Operand exprLvalue(NQJExprL e) {
        return e.match(exprLValue);
    }

    Operand exprRvalue(NQJExpr e) {
        return e.match(exprRValue);
    }

    void addNullcheck(Operand arrayAddr, String errorMessage) {
        TemporaryVar isNull = Ast.TemporaryVar("isNull");
        addInstruction(Ast.BinaryOperation(isNull, arrayAddr.copy(), Eq(), Nullpointer()));

        BasicBlock whenIsNull = newBasicBlock("whenIsNull");
        BasicBlock notNull = newBasicBlock("notNull");
        currentBlock.add(Branch(Ast.VarRef(isNull), whenIsNull, notNull));

        addBasicBlock(whenIsNull);
        whenIsNull.add(HaltWithError(errorMessage));

        addBasicBlock(notNull);
        setCurrentBlock(notNull);
    }

    Operand getArrayLen(Operand arrayAddr) {
        TemporaryVar addr = Ast.TemporaryVar("length_addr");
        addInstruction(GetElementPtr(addr,
                arrayAddr.copy(), OperandList(ConstInt(0), ConstInt(0))));
        TemporaryVar len = Ast.TemporaryVar("len");
        addInstruction(Load(len, Ast.VarRef(addr)));
        return Ast.VarRef(len);
    }

    public Operand getNewArrayFunc(Type componentType) {
        Proc proc = newArrayFuncForType.computeIfAbsent(componentType, this::createNewArrayProc);
        return ProcedureRef(proc);
    }

    private Proc createNewArrayProc(Type componentType) {
        Parameter size = Parameter(TypeInt(), "size");
        return Proc("newArray",
                getArrayPointerType(componentType), ParameterList(size), BasicBlockList());
    }

    private Type getArrayPointerType(Type componentType) {
        return TypePointer(getArrayStruct(componentType));
    }

    TypeStruct getArrayStruct(Type type) {
        return arrayStruct.computeIfAbsent(type, t -> {
            TypeStruct struct = TypeStruct("array_" + type, StructFieldList(
                    StructField(TypeInt(), "length"),
                    StructField(TypeArray(type, 0), "data")
            ));
            prog.getStructTypes().add(struct);
            return struct;
        });
    }

    Operand addCastIfNecessary(Operand value, Type expectedType) {
        if (expectedType.equalsType(value.calculateType())) {
            return value;
        }
        TemporaryVar castValue = Ast.TemporaryVar("castValue");
        addInstruction(Bitcast(castValue, expectedType, value));
        return Ast.VarRef(castValue);
    }

    BasicBlock unreachableBlock() {
        return BasicBlock();
    }

    Type getCurrentReturnType() {
        return currentProcedure.getReturnType();
    }

    public Optional<Proc> loadFunctionProc(NQJFunctionDecl functionDeclaration) {
        Proc proc = functionImpl.get(functionDeclaration);
        return proc == null ? Optional.empty() : Optional.of(proc);
    }

    public void storeFunctionProc(NQJFunctionDecl functionDeclaration, Proc proc) {
        functionImpl.put(functionDeclaration, proc);
    }

    /**
     * return the number of bytes required by the given type.
     */
    public Operand byteSize(Type type) {
        return type.match(new Type.Matcher<>() {
            @Override
            public Operand case_TypeByte(TypeByte typeByte) {
                return ConstInt(1);
            }

            @Override
            public Operand case_TypeArray(TypeArray typeArray) {
                throw new RuntimeException("TODO implement");
            }

            @Override
            public Operand case_TypeProc(TypeProc typeProc) {
                throw new RuntimeException("TODO implement");
            }

            @Override
            public Operand case_TypeInt(TypeInt typeInt) {
                return ConstInt(4);
            }

            @Override
            public Operand case_TypeStruct(TypeStruct typeStruct) {
                return Sizeof(typeStruct);
            }

            @Override
            public Operand case_TypeNullpointer(TypeNullpointer typeNullpointer) {
                return ConstInt(8);
            }

            @Override
            public Operand case_TypeVoid(TypeVoid typeVoid) {
                return ConstInt(0);
            }

            @Override
            public Operand case_TypeBool(TypeBool typeBool) {
                return ConstInt(1);
            }

            @Override
            public Operand case_TypePointer(TypePointer typePointer) {
                return ConstInt(8);
            }
        });
    }

    private Operand defaultValue(Type componentType) {
        return componentType.match(new Type.Matcher<>() {
            @Override
            public Operand case_TypeByte(TypeByte typeByte) {
                throw new RuntimeException("TODO implement");
            }

            @Override
            public Operand case_TypeArray(TypeArray typeArray) {
                throw new RuntimeException("TODO implement");
            }

            @Override
            public Operand case_TypeProc(TypeProc typeProc) {
                throw new RuntimeException("TODO implement");
            }

            @Override
            public Operand case_TypeInt(TypeInt typeInt) {
                return ConstInt(0);
            }

            @Override
            public Operand case_TypeStruct(TypeStruct typeStruct) {
                throw new RuntimeException("TODO implement");
            }

            @Override
            public Operand case_TypeNullpointer(TypeNullpointer typeNullpointer) {
                return Nullpointer();
            }

            @Override
            public Operand case_TypeVoid(TypeVoid typeVoid) {
                throw new RuntimeException("TODO implement");
            }

            @Override
            public Operand case_TypeBool(TypeBool typeBool) {
                return ConstBool(false);
            }

            @Override
            public Operand case_TypePointer(TypePointer typePointer) {
                return Nullpointer();
            }
        });
    }

    /**
     * Returns a class declaration based on the name of the class.
     *
     * @param name Name of the class
     */

    public NQJClassDecl getClassFromDeclarationList(String name) {
        Optional<NQJClassDecl> declOptional = this.classDeclList
                .stream()
                .filter(decl -> decl.getName().equals(name))
                .findAny();
        return declOptional.orElse(null);
    }


    void addBasicBlock(BasicBlock block) {
        currentProcedure.getBasicBlocks().add(block);
    }

    BasicBlock getCurrentBlock() {
        return currentBlock;
    }

    void setCurrentBlock(BasicBlock currentBlock) {
        this.currentBlock = currentBlock;
    }


    void addProcedure(Proc proc) {
        prog.getProcedures().add(proc);
    }

    void setCurrentProc(Proc currentProc) {
        if (currentProc == null) {
            throw new RuntimeException("Cannot set proc to null");
        }
        this.currentProcedure = currentProc;
    }

    void addInstruction(Instruction instruction) {
        currentBlock.add(instruction);
    }
}
