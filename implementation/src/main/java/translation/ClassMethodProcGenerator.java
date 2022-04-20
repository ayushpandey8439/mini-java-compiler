package translation;

import minillvm.ast.*;
import notquitejava.ast.*;

import java.util.Optional;

import static minillvm.ast.Ast.ProcedureRef;

/**
 * Generates Procs for class methods.
 */

public class ClassMethodProcGenerator extends NQJClassDeclList.DefaultVisitor {
    private final Translator translator;
    private final TranslationTable translationTable;

    /**
     * Initialises the translator for translating member methods of the class.
     *
     * @param translator       the instance of translator.
     * @param translationTable Table containing the data used for translation.
     */
    public ClassMethodProcGenerator(
            Translator translator,
            TranslationTable translationTable) {
        this.translator = translator;
        this.translationTable = translationTable;
    }

    @Override
    public void visit(NQJClassDecl classDecl) {
        String className = classDecl.getName();
        if (translator.handledClasses.add(className)) {
            TypeStruct classMemberStruct = translationTable.getMemberStruct(className).get();

            Global classGlobalStruct = translationTable.getClassGlobal(className).get();
            ConstList globalConstList = ((ConstStruct) classGlobalStruct
                    .getInitialValue())
                    .getValues();


            if (!(classDecl.getExtended() instanceof NQJExtendsNothing)) {
                NQJExtendsClass extendedClassReference = (NQJExtendsClass) classDecl.getExtended();
                NQJClassDecl extendedClass = translator
                        .getClassFromDeclarationList(extendedClassReference.getName());
                extendedClass.accept(this);

                StructFieldList inheritedMethods = translationTable
                        .getMemberStruct(extendedClass.getName())
                        .get()
                        .getFields();
                for (StructField inheritedMethod : inheritedMethods) {
                    translationTable
                            .setMethodAddressOffset(
                                    className + "_Class_Struct_" + inheritedMethod.getName(),
                                    classMemberStruct.getFields().size());

                    classMemberStruct.getFields().add(Ast.StructField(
                            Ast.TypePointer(
                                    ((TypePointer) inheritedMethod.getType()).getTo().copy()
                            ),
                            inheritedMethod.getName()
                    ));
                }

                ConstList structConsts = ((ConstStruct) translationTable
                        .getClassGlobal(extendedClass.getName())
                        .get()
                        .getInitialValue())
                        .getValues();
                for (Const structConst : structConsts) {
                    globalConstList.add(structConst.copy());
                }
            }

            TypeStruct classStruct = translationTable.getClassTypeStruct(className).get();
            NQJFunctionDeclList classMethods = classDecl.getMethods();
            for (NQJFunctionDecl methodDecl : classMethods) {
                Parameter classReference = Ast.Parameter(Ast.TypePointer(classStruct), "this");
                ParameterList parameterList = Ast.ParameterList(classReference);

                TypeRefList paramTypes = Ast.TypeRefList(Ast.TypePointer(classStruct));
                Type returnType = translator.translateType(methodDecl.getReturnType());

                // create a block for the body of the method.
                BasicBlock defaultBlock = translator.newBasicBlock(methodDecl.getName());
                translationTable.setCurrentBasicBlockList(Ast.BasicBlockList(defaultBlock));
                for (NQJVarDecl varDecl : methodDecl.getFormalParameters()) {
                    Type expectedType = translator.translateType(varDecl.getType());
                    Parameter formalParam = Ast.Parameter(expectedType, varDecl.getName());
                    parameterList.add(formalParam);
                    paramTypes.add(expectedType);
                }

                Proc procMethod = Ast.Proc(
                        classDecl.getName() + "-" + methodDecl.getName(),
                        returnType,
                        parameterList,
                        translationTable.getCurrentBasicBlockList()
                );

                Optional<StructField> parentStructField = classMemberStruct.getFields()
                        .stream()
                        .filter(structField -> structField
                                .getName()
                                .equals(methodDecl.getName())
                        ).findAny();

                int index = classMemberStruct.getFields().size();
                if (parentStructField.isPresent()) {
                    // If a procedure has been generated from the parent class
                    // and there is a local definition available, remove it from
                    // the procedure and add the new definition.
                    // The local definition is used in a local scope.
                    index = classMemberStruct.getFields().indexOf(parentStructField.get());
                    globalConstList.remove(index);

                    // Update the reference in the struct to the method new pointer.
                    ((TypeProc) ((TypePointer) parentStructField.get()
                            .getType())
                            .getTo())
                            .getArgTypes()
                            .set(0, classReference.getType());

                    ((TypeProc) ((TypePointer) parentStructField.get()
                            .getType())
                            .getTo())
                            .setResultType(returnType);


                } else {
                    classMemberStruct.getFields().add(
                            Ast.StructField(
                                    Ast.TypePointer(Ast.TypeProc(paramTypes, returnType)),
                                    methodDecl.getName()
                            )
                    );
                }
                // Add the new procedure at the index from which the inherited method was removed.
                globalConstList.add(index, ProcedureRef(procMethod));
                translator.addProcedure(procMethod);
                translator.storeFunctionProc(methodDecl, procMethod);
                translationTable.setMethodAddressOffset(
                        classDecl.getName() + "_Class_Struct_" + methodDecl.getName(),
                        index);
            }


        }
        super.visit(classDecl);
    }
}
