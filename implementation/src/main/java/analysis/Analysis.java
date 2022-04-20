package analysis;

import java.util.*;

import notquitejava.ast.*;


/**
 * Analysis visitor to handle most of the type rules specified to NQJ.
 */
public class Analysis extends NQJElement.DefaultVisitor {
    private final NQJProgram prog;
    private final List<TypeError> typeErrors = new ArrayList<>();
    private NameTable nameTable;
    private final LinkedList<TypeContext> ctxt = new LinkedList<>();

    NQJClassDecl currentClass;

    public void addError(NQJElement element, String message) {
        typeErrors.add(new TypeError(element, message));
    }

    public Analysis(NQJProgram prog) {
        this.prog = prog;
    }

    /**
     * Checks the saves NQJProgram for type errors.
     * Main entry point for type checking.
     */
    public void check() {
        nameTable = new NameTable(this, prog);

        verifyMainMethod();

        prog.accept(this);
    }

    private void verifyMainMethod() {
        var main = nameTable.lookupFunction("main");
        if (main == null) {
            typeErrors.add(new TypeError(prog, "Method int main() must be present"));
            return;
        }
        if (!(main.getReturnType() instanceof NQJTypeInt)) {
            typeErrors.add(new TypeError(main.getReturnType(),
                    "Return type of the main method must be int"));
        }
        if (!(main.getFormalParameters().isEmpty())) {
            typeErrors.add(new TypeError(main.getFormalParameters(),
                    "Main method does not take parameters"));
        }
        // Check if return statement is there as the last statement
        NQJStatement last = null;
        for (NQJStatement nqjStatement : main.getMethodBody()) {
            last = nqjStatement;
        }
        // TODO this forces the main method to have a single return at the end
        //      instead check for all possible paths to end in a return statement
        if (!(last instanceof NQJStmtReturn)) {
            typeErrors.add(new TypeError(main.getFormalParameters(),
                    "Main method does not have a return statement as the last statement"));
        }
    }


    @Override
    public void visit(NQJProgram program) {
        super.visit(program);
    }

    @Override
    public void visit(NQJClassDeclList classDeclList) {
        boolean inheritanceCycle = checkInheritanceCycle();
        if (inheritanceCycle) {
            return;
        }
        super.visit(classDeclList);
    }

    @Override
    public void visit(NQJExtendsClass extendsClass) {


        NQJClassDecl classDecl = nameTable.lookupClass(extendsClass.getName());
        NQJVarDeclList fields = classDecl.getFields();
        TypeContext mctxt = this.ctxt.isEmpty()
                ? new TypeContextImpl(null, Type.INVALID)
                : this.ctxt.peek().copy();

        for (NQJVarDecl field : fields) {
            Set<String> paramNames = new HashSet<>();
            if (!paramNames.add(field.getName())) {
                addError(field, "Field with name " + field.getName()
                        + " already exists in class " + extendsClass.getName() + ".");
            }
            mctxt.putVar(field.getName(), type(field.getType()), field);
        }

        ctxt.push(mctxt);
        super.visit(extendsClass);
    }

    @Override
    public void visit(NQJClassDecl classDecl) {
        this.currentClass = classDecl;
        classDecl.getExtended().accept(this);


        NQJVarDeclList fields = classDecl.getFields();

        // Check duplicate variables in a class and add them to the context.
        TypeContext classCtxt = new TypeContextImpl(null, Type.INVALID);

        // enter Class context
        ctxt.push(classCtxt);


        fields.accept(this);

        NQJFunctionDeclList methods = classDecl.getMethods();
        // Check duplicate methods in the class
        Set<String> methodNames = new HashSet<>();
        for (NQJFunctionDecl method : methods) {
            if (!methodNames.add(method.getName())) {
                addError(method, "Duplicate method declaration.");
            }
        }

        methods.accept(this);
        this.currentClass = null;
        ctxt.pop();
    }

    @Override
    public void visit(NQJFunctionDecl m) {
        NQJVarDeclList expectedFormalParameters = m.getFormalParameters();
        if (this.currentClass != null) {
            //This block checks for correct method overriding.
            if (!(this.currentClass.getExtended() instanceof NQJExtendsNothing)) {
                Optional<NQJFunctionDecl> methodDecl = nameTable.getMethodFromClassHierarchy(
                        ((NQJExtendsClass) this.currentClass.getExtended()).getName(), m.getName()
                );

                if (methodDecl.isPresent()) {
                    NQJVarDeclList parentFormalParameters = methodDecl.get().getFormalParameters();
                    // Check if the formal parameters of any method in a parent class is the same.
                    if (!(expectedFormalParameters.size() == parentFormalParameters.size())) {
                        addError(m,
                                "The number of parameters supplied for overriding does not match.");
                    }

                    for (int i = 0; i < expectedFormalParameters.size(); i++) {
                        NQJType parentType = parentFormalParameters.get(i).getType();
                        NQJType expectedType = expectedFormalParameters.get(i).getType();
                        if (!(parentType.structuralEquals(expectedType))) {
                            addError(m, "The type of one or more parameters"
                                    + " does not match the overridden method.");
                        }
                    }

                    Type methodType = type(methodDecl.get().getReturnType());

                    boolean returnTypeMatches = type(m.getReturnType())
                            .isSubtypeOf(methodType, nameTable);

                    if (!returnTypeMatches) {
                        addError(m, "Invalid overriding of method "
                                + m.getName() + " in child class " + currentClass.getName());
                    }
                }
            }
        }

        // add the fields from the last context here.
        TypeContext mctxt = new TypeContextImpl(null, type(m.getReturnType()));
        mctxt.setReturnType(type(m.getReturnType()));

        for (NQJVarDecl param : expectedFormalParameters) {
            Set<String> paramNames = new HashSet<>();
            if (!paramNames.add(param.getName())) {
                addError(m, "Parameter with name " + param.getName() + " already exists.");
            }
            mctxt.putVar(param.getName(), type(param.getType()), param);
        }


        NQJStatement last = null;
        for (NQJStatement nqjStatement : m.getMethodBody()) {
            last = nqjStatement;
        }
        if (!(last instanceof NQJStmtReturn)) {
            typeErrors.add(new TypeError(m.getFormalParameters(),
                    "Method " + m.getName() + " does not return anything"));
        }
        // enter method context
        ctxt.push(mctxt);

        m.getMethodBody().accept(this);

        ctxt.pop();

    }


    @Override
    public void visit(NQJStmtReturn stmtReturn) {
        Type actualReturn = checkExpr(ctxt, stmtReturn.getResult());
        assert ctxt.peek() != null;
        Type expectedReturn = ctxt.peek().getReturnType();
        if (!actualReturn.isSubtypeOf(expectedReturn, nameTable)) {
            addError(stmtReturn, "Should return value of type " + expectedReturn
                    + ", but found " + actualReturn + ".");
        }
    }

    @Override
    public void visit(NQJStmtAssign stmtAssign) {
        Type lt = checkExpr(ctxt, stmtAssign.getAddress());
        Type rt = checkExpr(ctxt, stmtAssign.getValue());

        if (!rt.isSubtypeOf(lt, nameTable)) {
            addError(stmtAssign.getValue(), "Incompatible types in the assignment.");
        }
    }

    @Override
    public void visit(NQJStmtExpr stmtExpr) {
        checkExpr(ctxt, stmtExpr.getExpr());
    }

    @Override
    public void visit(NQJStmtWhile stmtWhile) {
        Type ct = checkExpr(ctxt, stmtWhile.getCondition());
        if (!ct.isSubtypeOf(Type.BOOL, nameTable)) {
            addError(stmtWhile.getCondition(),
                    "Condition of while-statement must be of type boolean, but this is of type "
                            + ct + ".");
        }
        super.visit(stmtWhile);
    }

    @Override
    public void visit(NQJStmtIf stmtIf) {
        Type ct = checkExpr(ctxt, stmtIf.getCondition());
        if (!ct.isSubtypeOf(Type.BOOL, nameTable)) {
            addError(stmtIf.getCondition(),
                    "Condition of if-statement must be of type boolean, but this is of type "
                            + ct + ".");
        }
        super.visit(stmtIf);
    }

    @Override
    public void visit(NQJBlock block) {

        TypeContext bctxt = this.ctxt.isEmpty()
                ? new TypeContextImpl(null, Type.INVALID)
                : this.ctxt.peek().copy();
        // enter block context
        ctxt.push(bctxt);
        for (NQJStatement s : block) {
            s.accept(this);
            // exit block context
        }
        ctxt.pop();
    }

    @Override
    public void visit(NQJVarDecl varDecl) {
        TypeContext bctxt = ctxt.peek();
        assert bctxt != null;
        TypeContextImpl.VarRef ref = bctxt.lookupVar(varDecl.getName());
        if (ref != null) {
            addError(varDecl, "A variable with name " + varDecl.getName()
                    + " is already defined.");
        }
        bctxt.putVar(varDecl.getName(), type(varDecl.getType()), varDecl);
        super.visit(varDecl);
    }


    public Type checkExpr(LinkedList<TypeContext> ctxt, NQJExpr e) {
        return e.match(new ExprChecker(this, ctxt, nameTable));
    }

    public Type checkExpr(LinkedList<TypeContext> ctxt, NQJExprL e) {
        return e.match(new ExprChecker(this, ctxt, nameTable));
    }

    /**
     * NQJ AST element to Type converter.
     */
    public Type type(NQJType type) {
        Type result = type.match(new NQJType.Matcher<>() {


            @Override
            public Type case_TypeBool(NQJTypeBool typeBool) {
                return Type.BOOL;
            }

            @Override
            public Type case_TypeClass(NQJTypeClass typeClass) {
                Optional<NQJClassDecl> classDecl = nameTable.getClassDefinitions()
                        .stream()
                        .filter(cd -> cd.getName().equals(typeClass.getName())).findAny();

                if (classDecl.isPresent()) {
                    return new ClassType(classDecl.get());
                } else {
                    addError(typeClass, "The class for this definition does not exist.");
                    return Type.INVALID;
                }

            }

            @Override
            public Type case_TypeArray(NQJTypeArray typeArray) {
                return nameTable.getArrayType(type(typeArray.getComponentType()));
            }

            @Override
            public Type case_TypeInt(NQJTypeInt typeInt) {
                return Type.INT;
            }

        });

        type.setType(result);
        return result;
    }

    public NameTable getNameTable() {
        return nameTable;
    }

    public List<TypeError> getTypeErrors() {
        return new ArrayList<>(typeErrors);
    }

    private boolean checkInheritanceCycleRecursive(NQJClassDecl classDecl, int step) {
        if (step > nameTable.getClassDefinitions().size()) {
            addError(classDecl, "Inheritance cycle detected in the class.");
            return true;
        } else if (classDecl == null) {
            addError(nameTable.getClassDefinitions(), "Inherited class does not exist.");
            return true;
        }


        if (!(classDecl.getExtended() instanceof NQJExtendsNothing)) {
            String extendedClass = ((NQJExtendsClass) classDecl.getExtended()).getName();
            return checkInheritanceCycleRecursive(nameTable.lookupClass(extendedClass), step + 1);
        }

        return false;
    }


    private boolean checkInheritanceCycle() {
        boolean foundCycle = false;
        for (NQJClassDecl i : getNameTable().getClassDefinitions()) {
            foundCycle = foundCycle || checkInheritanceCycleRecursive(i, 0);
        }

        return foundCycle;

    }

}
