package translation;

import minillvm.ast.*;
import notquitejava.ast.NQJClassDecl;
import notquitejava.ast.NQJClassDeclList;
import static minillvm.ast.Ast.*;
import java.util.Set;

import static minillvm.ast.Ast.VarRef;

/**
 * Generates the implicit constructor for a class.
 */
public class ClassConstructorGenerator extends NQJClassDeclList.DefaultVisitor {
    private final Translator translator;
    private final TranslationTable translationTable;

    public ClassConstructorGenerator(Translator translator, TranslationTable translationTable) {
        this.translator = translator;
        this.translationTable = translationTable;
    }

    @Override
    public void visit(NQJClassDecl classDecl) {
        String className = classDecl.getName();
        //Create a new basic block for the constructor body.
        BasicBlock constructorBody = translator.newBasicBlock(className
                + "_Constructor_Body");

        translationTable.getConstructorProc(className).get().getBasicBlocks().add(constructorBody);

        TemporaryVar classStructTemporary = TemporaryVar("Class_Struct");

        TypeStruct classStruct = translationTable.getClassTypeStruct(className).get();

        //Allocate space for the class object when the constructor is invoked.
        constructorBody.add(Alloc(classStructTemporary, translator.byteSize(classStruct)));

        TemporaryVar classTypeTemporary = TemporaryVar("Constructor_Type");
        constructorBody.add(Bitcast(classTypeTemporary, TypePointer(classStruct),
                VarRef(classStructTemporary)));

        // Add references to the fields of the class which are global.
        StructFieldList classFields = classStruct.getFields();
        TypeStruct classFieldStruct = translationTable.getMemberStruct(className).get();
        classFields.add(StructField(TypePointer(classFieldStruct), className + "_Fields"));

        // Get the fields from the class hierarchy. These fields are automatically added to the
        // class definition and are initialised by the constructor.
        translator.getDefinitionsFromClassHierarchy(classDecl, classFields);


        classTypeTemporary.calculateType().match(
                new InitialisationTranslator(
                        VarRef(classTypeTemporary),
                        true,
                        false,
                        constructorBody
                )
        );

        TemporaryVar classStructReference = TemporaryVar("Class_Reference");
        // Add a reference to the struct containing the fields of the class.
        constructorBody.add(GetElementPtr(classStructReference, VarRef(classTypeTemporary),
                OperandList(ConstInt(0), ConstInt(0))));

        Global classGlobal = translationTable.getClassGlobal(className).get();
        // Add a reference to the class Global to the constructor
        constructorBody.add(
                Store(
                        VarRef(classStructReference),
                        GlobalRef(classGlobal)
                ));

        constructorBody.add(ReturnExpr(VarRef(classTypeTemporary)));

        super.visit(classDecl);
    }
}
