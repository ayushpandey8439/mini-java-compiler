package translation;

import minillvm.ast.*;
import notquitejava.ast.*;

import static minillvm.ast.Ast.*;

/**
 * Evaluate r values.
 */
public class ExprRValue implements NQJExpr.Matcher<Operand> {
    private final Translator tr;
    private final TranslationTable translationTable;

    public ExprRValue(Translator translator, TranslationTable translationTable) {
        this.tr = translator;
        this.translationTable = translationTable;
    }

    @Override
    public Operand case_ExprUnary(NQJExprUnary e) {
        Operand expr = tr.exprRvalue(e.getExpr());

        return e.getUnaryOperator().match(new NQJUnaryOperator.Matcher<>() {

            @Override
            public Operand case_UnaryMinus(NQJUnaryMinus unaryMinus) {
                TemporaryVar v = TemporaryVar("minus_res");
                tr.addInstruction(BinaryOperation(v, ConstInt(0), Ast.Sub(), expr));
                return VarRef(v);
            }

            @Override
            public Operand case_Negate(NQJNegate negate) {
                TemporaryVar v = TemporaryVar("neg_res");
                tr.addInstruction(BinaryOperation(v, Ast.ConstBool(false), Eq(), expr));
                return VarRef(v);
            }
        });
    }

    @Override
    public Operand case_ArrayLength(NQJArrayLength e) {
        Operand a = tr.exprRvalue(e.getArrayExpr());
        tr.addNullcheck(a,
                "Nullpointer exception when reading array length in line " + tr.sourceLine(e));
        return tr.getArrayLen(a);
    }

    @Override
    public Operand case_NewArray(NQJNewArray newArray) {
        Type componentType = tr.translateType(newArray.getArrayType().getBaseType());
        Operand arraySize = tr.exprRvalue(newArray.getArraySize());
        Operand proc = tr.getNewArrayFunc(componentType);
        TemporaryVar res = TemporaryVar("newArray");
        tr.addInstruction(Ast.Call(res, proc, OperandList(arraySize)));
        return VarRef(res);
    }

    @Override
    public Operand case_ExprBinary(NQJExprBinary e) {
        Operand left = tr.exprRvalue(e.getLeft());
        return e.getOperator().match(new NQJOperator.Matcher<>() {
            @Override
            public Operand case_And(NQJAnd and) {
                BasicBlock andRight = tr.newBasicBlock("and_first_true");
                BasicBlock andEnd = tr.newBasicBlock("and_end");
                TemporaryVar andResVar = TemporaryVar("andResVar");
                tr.getCurrentBlock().add(Ast.Alloca(andResVar, Ast.TypeBool()));
                tr.getCurrentBlock().add(Ast.Store(VarRef(andResVar), left));
                tr.getCurrentBlock().add(Ast.Branch(left.copy(), andRight, andEnd));

                tr.addBasicBlock(andRight);
                tr.setCurrentBlock(andRight);
                Operand right = tr.exprRvalue(e.getRight());
                tr.getCurrentBlock().add(Ast.Store(VarRef(andResVar), right));
                tr.getCurrentBlock().add(Ast.Jump(andEnd));

                tr.addBasicBlock(andEnd);
                tr.setCurrentBlock(andEnd);
                TemporaryVar andRes = TemporaryVar("andRes");
                andEnd.add(Ast.Load(andRes, VarRef(andResVar)));
                return VarRef(andRes);
            }


            private Operand normalCase(Operator op) {
                Operand right = tr.exprRvalue(e.getRight());
                TemporaryVar result = TemporaryVar("res" + op.getClass().getSimpleName());
                tr.addInstruction(BinaryOperation(result, left, op, right));
                return VarRef(result);
            }

            @Override
            public Operand case_Times(NQJTimes times) {
                return normalCase(Ast.Mul());
            }


            @Override
            public Operand case_Div(NQJDiv div) {
                Operand right = tr.exprRvalue(e.getRight());
                TemporaryVar divResVar = TemporaryVar("divResVar");
                tr.addInstruction(Ast.Alloca(divResVar, Ast.TypeInt()));
                TemporaryVar isZero = TemporaryVar("isZero");
                tr.addInstruction(BinaryOperation(isZero, right, Eq(), ConstInt(0)));
                BasicBlock ifZero = tr.newBasicBlock("ifZero");
                BasicBlock notZero = tr.newBasicBlock("notZero");

                tr.addInstruction(Ast.Branch(VarRef(isZero), ifZero, notZero));

                tr.addBasicBlock(ifZero);
                ifZero.add(Ast.HaltWithError("Division by zero in line " + tr.sourceLine(e)));


                tr.addBasicBlock(notZero);
                tr.setCurrentBlock(notZero);

                BasicBlock divEnd = tr.newBasicBlock("div_end");
                BasicBlock divNoOverflow = tr.newBasicBlock("div_noOverflow");

                TemporaryVar isMinusOne = TemporaryVar("isMinusOne");
                tr.addInstruction(BinaryOperation(isMinusOne,
                        right.copy(), Eq(), ConstInt(-1)));
                TemporaryVar isMinInt = TemporaryVar("isMinInt");
                tr.addInstruction(BinaryOperation(isMinInt,
                        left.copy(), Eq(), ConstInt(Integer.MIN_VALUE)));
                TemporaryVar isOverflow = TemporaryVar("isOverflow");
                tr.addInstruction(BinaryOperation(isOverflow,
                        VarRef(isMinInt), And(), VarRef(isMinusOne)));
                tr.addInstruction(Ast.Store(VarRef(divResVar), ConstInt(Integer.MIN_VALUE)));
                tr.addInstruction(Ast.Branch(VarRef(isOverflow), divEnd, divNoOverflow));


                tr.addBasicBlock(divNoOverflow);
                tr.setCurrentBlock(divNoOverflow);
                TemporaryVar divResultA = TemporaryVar("divResultA");
                tr.addInstruction(BinaryOperation(divResultA, left, Ast.Sdiv(), right.copy()));
                tr.addInstruction(Ast.Store(VarRef(divResVar), VarRef(divResultA)));
                tr.addInstruction(Ast.Jump(divEnd));


                tr.addBasicBlock(divEnd);
                tr.setCurrentBlock(divEnd);
                TemporaryVar divResultB = TemporaryVar("divResultB");
                tr.addInstruction(Ast.Load(divResultB, VarRef(divResVar)));
                return VarRef(divResultB);
            }

            @Override
            public Operand case_Plus(NQJPlus plus) {
                return normalCase(Ast.Add());
            }

            @Override
            public Operand case_Minus(NQJMinus minus) {
                return normalCase(Ast.Sub());
            }

            @Override
            public Operand case_Equals(NQJEquals equals) {
                Operator op = Eq();
                Operand right = tr.exprRvalue(e.getRight());
                TemporaryVar result = TemporaryVar("res" + op.getClass().getSimpleName());
                right = tr.addCastIfNecessary(right, left.calculateType());
                tr.addInstruction(BinaryOperation(result, left, op, right));
                return VarRef(result);
            }

            @Override
            public Operand case_Less(NQJLess less) {
                return normalCase(Ast.Slt());
            }
        });
    }

    @Override
    public Operand case_ExprNull(NQJExprNull e) {
        return Ast.Nullpointer();
    }

    @Override
    public Operand case_Number(NQJNumber e) {
        return ConstInt(e.getIntValue());
    }

    @Override
    public Operand case_FunctionCall(NQJFunctionCall e) {
        // special case: printInt
        if (e.getMethodName().equals("printInt")) {
            NQJExpr arg1 = e.getArguments().get(0);
            Operand op = tr.exprRvalue(arg1);
            tr.addInstruction(Ast.Print(op));
            return ConstInt(0);
        } else {
            NQJFunctionDecl functionDeclaration = e.getFunctionDeclaration();

            OperandList args = OperandList();
            for (int i = 0; i < e.getArguments().size(); i++) {
                Operand arg = tr.exprRvalue(e.getArguments().get(i));
                NQJVarDeclList formalParameters = functionDeclaration.getFormalParameters();
                arg = tr.addCastIfNecessary(arg, tr.translateType(
                        formalParameters.get(i).getType())
                );
                args.add(arg);
            }

            // lookup in global functions
            Proc proc = tr.loadFunctionProc(e.getFunctionDeclaration()).get();

            // do the call
            TemporaryVar result = TemporaryVar(e.getMethodName() + "_result");
            tr.addInstruction(Ast.Call(result, ProcedureRef(proc), args));
            return VarRef(result);
        }
    }

    @Override
    public Operand case_BoolConst(NQJBoolConst e) {
        return Ast.ConstBool(e.getBoolValue());
    }

    @Override
    public Operand case_Read(NQJRead read) {
        TemporaryVar res = TemporaryVar("t");
        Operand op = tr.exprLvalue(read.getAddress());
        tr.addInstruction(Ast.Load(res, op));
        return VarRef(res);
    }

    @Override
    public Operand case_MethodCall(NQJMethodCall e) {
        BasicBlock currentBlock = tr.getCurrentBlock();
        Operand receiver = tr.exprRvalue(e.getReceiver());

        TypeStruct receiverType = (TypeStruct) ((TypePointer) receiver.calculateType()).getTo();

        TemporaryVar fieldTablePointer = TemporaryVar("v_table_pointer");
        TemporaryVar fieldTable = TemporaryVar("fields");
        currentBlock.add(GetElementPtr(
                fieldTable,
                receiver,
                OperandList(ConstInt(0), ConstInt(0)))
        );
        currentBlock.add(Load(fieldTablePointer, VarRef(fieldTable)));
        TemporaryVar methodPointer = TemporaryVar("method_pointer");
        TemporaryVar method = TemporaryVar("method");
        int methodOffset = translationTable.getMethodAddressOffset(
                receiverType.getName() + "_" + e.getMethodName()
        );

        currentBlock.add(GetElementPtr(
                methodPointer,
                VarRef(fieldTablePointer),
                OperandList(ConstInt(0), ConstInt(methodOffset))
        ));
        currentBlock.add(Load(method, VarRef(methodPointer)));

        TypeProc functionType = (TypeProc) ((TypePointer) method.calculateType()).getTo();
        Type receiverObjectType = functionType.getArgTypes().get(0);
        Operand receiverObject = receiver.copy();
        if (!receiverObjectType.equalsType(receiverObject.calculateType())) {
            TemporaryVar subTypedObject = TemporaryVar("type_casted_object");
            currentBlock.add(Bitcast(subTypedObject, receiverObjectType, receiver.copy()));
            receiverObject = VarRef(subTypedObject);
        }

        OperandList arguments = OperandList();
        arguments.add(receiverObject);

        int index = 1;
        for (NQJExpr methodArgument : e.getArguments()) {
            Operand argument = tr.exprRvalue(methodArgument);

            Type expectedType = functionType.getArgTypes().get(index);
            Type suppliedType = argument.calculateType();
            if (!suppliedType.equalsType(expectedType)) {
                TemporaryVar castedArgument = TemporaryVar("type_casted_argument");
                currentBlock.add(Bitcast(castedArgument, expectedType, argument));
                arguments.add(VarRef(castedArgument));

            } else {
                arguments.add(argument.copy());
            }

            index++;
        }
        TemporaryVar returnPointer = TemporaryVar("return_pointer");
        tr.getCurrentBlock().add(Call(returnPointer, VarRef(method), arguments));
        return VarRef(returnPointer);

    }

    @Override
    public Operand case_NewObject(NQJNewObject e) {
        Proc objectClassConstructor = translationTable.getConstructorProc(e.getClassName()).get();
        TemporaryVar object = TemporaryVar(e.getClassName());
        tr.getCurrentBlock().add(Call(object, ProcedureRef(objectClassConstructor), OperandList()));
        return VarRef(object);
    }

    @Override
    public Operand case_ExprThis(NQJExprThis e) {
        return VarRef(tr.getThisParameter());
    }

}
