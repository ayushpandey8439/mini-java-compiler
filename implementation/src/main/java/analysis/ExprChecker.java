package analysis;

import notquitejava.ast.*;

import java.util.LinkedList;
import java.util.Optional;

/**
 * Matcher implementation for expressions returning a NQJ type.
 */
public class ExprChecker implements NQJExpr.Matcher<Type>, NQJExprL.Matcher<Type> {
    private final Analysis analysis;
    private final LinkedList<TypeContext> ctxt;
    private final NameTable nameTable;

    /**
     * Matcher initialization.
     */

    public ExprChecker(Analysis analysis, LinkedList<TypeContext> ctxt, NameTable nameTable) {
        this.analysis = analysis;
        this.ctxt = ctxt;
        this.nameTable = nameTable;
    }

    Type check(NQJExpr e) {
        return e.match(this);
    }

    Type check(NQJExprL e) {
        return e.match(this);
    }

    void expect(NQJExpr e, Type expected) {
        Type actual = check(e);
        if (!actual.isSubtypeOf(expected, this.nameTable)) {
            analysis.addError(e, "Expected expression of type " + expected
                    + " but found " + actual + ".");
        }
    }


    Type expectArray(NQJExpr e) {
        Type actual = check(e);
        if (!(actual instanceof ArrayType)) {
            analysis.addError(e, "Expected expression of array type,  but found " + actual + ".");
            return Type.ANY;
        } else {
            return actual;
        }
    }

    @Override
    public Type case_ExprUnary(NQJExprUnary exprUnary) {
        check(exprUnary.getExpr());
        return exprUnary.getUnaryOperator().match(new NQJUnaryOperator.Matcher<Type>() {

            @Override
            public Type case_UnaryMinus(NQJUnaryMinus unaryMinus) {
                expect(exprUnary.getExpr(), Type.INT);
                return Type.INT;
            }

            @Override
            public Type case_Negate(NQJNegate negate) {
                expect(exprUnary.getExpr(), Type.BOOL);
                return Type.BOOL;
            }
        });
    }

    @Override
    public Type case_MethodCall(NQJMethodCall methodCall) {
        Type receiverType = methodCall.getReceiver()
                .match(new ExprChecker(this.analysis, this.ctxt, this.nameTable));

        if (!(receiverType.toString().equals("class"))) {
            analysis.addError(methodCall, "Cannot call method on the primitive type.");
            return Type.INVALID;
        }
        NQJClassDecl classDecl = ((ClassType) receiverType).getClassDecl();

        Optional<NQJFunctionDecl> methodDecl = nameTable.getMethodFromClassHierarchy(
                classDecl.getName(),
                methodCall.getMethodName()
        );

        if (methodDecl.isEmpty()) {
            analysis.addError(methodCall,
                    "Cannot find the method in the parent class " + classDecl.getName() + ".");
            return Type.INVALID;
        }

        NQJExprList suppliedParams = methodCall.getArguments();
        NQJVarDeclList expectedParams = methodDecl.get().getFormalParameters();
        if (suppliedParams.size() != expectedParams.size()) {
            analysis.addError(methodCall,
                    "The number of arguments supplied do not match with the function definition.");
            return Type.INVALID;
        }

        for (int i = 0; i < suppliedParams.size(); i++) {
            Type suppliedType = suppliedParams.get(i)
                    .match(new ExprChecker(this.analysis, this.ctxt, this.nameTable));
            Type expectedType = expectedParams.get(i).getType()
                    .match(new TypeMatcher(this.analysis, this.ctxt.peek(), this.nameTable));
            //TODO: The class is returning null here.
            if (!suppliedType.isSubtypeOf(expectedType, nameTable)) {
                analysis.addError(methodCall,
                        "Type of the arguments supplied does not match the function definition");
                return Type.INVALID;
            }
        }

        return type(methodDecl.get().getReturnType());
    }


    @Override
    public Type case_ArrayLength(NQJArrayLength arrayLength) {
        expectArray(arrayLength.getArrayExpr());
        return Type.INT;
    }

    @Override
    public Type case_ExprThis(NQJExprThis exprThis) {
        //TODO: Check if there is a current class active and then the fields are there or not.
        if (analysis.currentClass != null) {
            return new ClassType(analysis.currentClass);
        } else {
            analysis.addError(exprThis, "Cannot refer to a class with the 'this' keyword.");
            return Type.INVALID;
        }
    }

    @Override
    public Type case_ExprBinary(NQJExprBinary exprBinary) {
        return exprBinary.getOperator().match(new NQJOperator.Matcher<>() {
            @Override
            public Type case_And(NQJAnd and) {
                expect(exprBinary.getLeft(), Type.BOOL);
                expect(exprBinary.getRight(), Type.BOOL);
                return Type.BOOL;
            }

            @Override
            public Type case_Times(NQJTimes times) {
                return case_intOperation();
            }

            @Override
            public Type case_Div(NQJDiv div) {
                return case_intOperation();
            }

            @Override
            public Type case_Plus(NQJPlus plus) {
                return case_intOperation();
            }

            @Override
            public Type case_Minus(NQJMinus minus) {
                return case_intOperation();
            }

            private Type case_intOperation() {
                expect(exprBinary.getLeft(), Type.INT);
                expect(exprBinary.getRight(), Type.INT);
                return Type.INT;
            }

            @Override
            public Type case_Equals(NQJEquals equals) {
                Type l = check(exprBinary.getLeft());
                Type r = check(exprBinary.getRight());
                if (!l.isSubtypeOf(r, nameTable) && !r.isSubtypeOf(l, nameTable)) {
                    analysis.addError(exprBinary, "Cannot compare types " + l + " and " + r + ".");
                }
                return Type.BOOL;
            }

            @Override
            public Type case_Less(NQJLess less) {
                expect(exprBinary.getLeft(), Type.INT);
                expect(exprBinary.getRight(), Type.INT);
                return Type.BOOL;
            }
        });
    }

    @Override
    public Type case_ExprNull(NQJExprNull exprNull) {
        return Type.NULL;
    }

    @Override
    public Type case_FunctionCall(NQJFunctionCall functionCall) {
        NQJFunctionDecl m = analysis.getNameTable().lookupFunction(functionCall.getMethodName());
        if (m == null) {
            analysis.addError(functionCall, "Function " + functionCall.getMethodName()
                    + " does not exists.");
            return Type.ANY;
        }
        NQJExprList args = functionCall.getArguments();
        NQJVarDeclList params = m.getFormalParameters();
        if (args.size() < params.size()) {
            analysis.addError(functionCall, "Not enough arguments.");
        } else if (args.size() > params.size()) {
            analysis.addError(functionCall, "Too many arguments.");
        } else {
            for (int i = 0; i < params.size(); i++) {
                expect(args.get(i), analysis.type(params.get(i).getType()));
            }
        }
        functionCall.setFunctionDeclaration(m);
        return analysis.type(m.getReturnType());
    }

    @Override
    public Type case_Number(NQJNumber number) {
        return Type.INT;
    }

    @Override
    public Type case_NewArray(NQJNewArray newArray) {
        expect(newArray.getArraySize(), Type.INT);
        ArrayType t = new ArrayType(analysis.type(newArray.getBaseType()));
        newArray.setArrayType(t);
        return t;
    }

    @Override
    public Type case_NewObject(NQJNewObject newObject) {
        boolean classExists = (nameTable.lookupClass(newObject.getClassName()) != null);
        if (!classExists) {
            analysis.addError(newObject, "The class for the object definition does not exist.");
            return Type.INVALID;
        }
        return new ClassType(nameTable.lookupClass(newObject.getClassName()));
    }

    @Override
    public Type case_BoolConst(NQJBoolConst boolConst) {
        return Type.BOOL;
    }

    @Override
    public Type case_Read(NQJRead read) {
        return read.getAddress().match(this);
    }

    @Override
    public Type case_FieldAccess(NQJFieldAccess fieldAccess) {
        Type receiverType = fieldAccess.getReceiver()
                .match(new ExprChecker(this.analysis, this.ctxt, this.nameTable));
        String fieldAccessed = fieldAccess.getFieldName();
        if (!receiverType.toString().equals("class")) {
            analysis.addError(fieldAccess, "Cannot access a field from a primitive type");
            return Type.INVALID;
        } else {
            Optional<NQJVarDecl> varDecl =
                    nameTable.getFieldFromClass(
                            ((ClassType) receiverType).getClassDecl().getName(),
                            fieldAccessed);
            if (varDecl.isEmpty()) {
                analysis.addError(fieldAccess, "The accessed field does not exist in the class.");
                return Type.INVALID;
            } else {
                fieldAccess.setVariableDeclaration(varDecl.get());
                return varDecl.get().getType()
                        .match(new TypeMatcher(this.analysis, this.ctxt.peek(), this.nameTable));
            }
        }
    }

    @Override
    public Type case_VarUse(NQJVarUse varUse) {
        //At the variable use, climb up in the hierarchy and see if it is present.
        Optional<TypeContext> context = ctxt
                .stream()
                .filter(c -> c.lookupVar(varUse.getVarName()) != null)
                .findAny();
        if (context.isEmpty()) {
            analysis.addError(varUse, "Variable " + varUse.getVarName() + " is not defined.");
            return Type.ANY;
        }
        NQJVarDecl decl = context.get().lookupVar(varUse.getVarName()).decl;
        varUse.setVariableDeclaration(decl);
        //return ref.type;

        return type(decl.getType());
    }

    @Override
    public Type case_ArrayLookup(NQJArrayLookup arrayLookup) {
        Type type = analysis.checkExpr(ctxt, arrayLookup.getArrayExpr());
        expect(arrayLookup.getArrayIndex(), Type.INT);
        if (type instanceof ArrayType) {
            ArrayType arrayType = (ArrayType) type;
            arrayLookup.setArrayType(arrayType);
            return arrayType.getBaseType();
        }
        analysis.addError(arrayLookup, "Expected an array for array-lookup, but found " + type);
        return Type.ANY;
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
                    analysis.addError(typeClass, "The class for this definition does not exist.");
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
}
