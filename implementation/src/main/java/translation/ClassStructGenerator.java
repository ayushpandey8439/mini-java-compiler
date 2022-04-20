package translation;

import minillvm.ast.*;
import notquitejava.ast.NQJClassDecl;
import notquitejava.ast.NQJClassDeclList;


/**
 * Generates struct types for the class definitions.
 */
public class ClassStructGenerator extends NQJClassDeclList.DefaultVisitor {
    private final Translator translator;
    private final Prog prog;
    private final TranslationTable translationTable;

    /**
     * Initialise the struct generator.
     *
     * @param translator       the instance of translator.
     * @param prog             the translated program.
     * @param translationTable Table containing the data used for translation.
     */
    public ClassStructGenerator(
            Translator translator,
            Prog prog,
            TranslationTable translationTable
    ) {
        this.translator = translator;
        this.prog = prog;
        this.translationTable = translationTable;
    }

    @Override
    public void visit(NQJClassDecl classDecl) {
        String className = classDecl.getName();
        if (translationTable.getClassTypeStruct(className).isEmpty()) {
            //Create a new class Type struct. This will contain a pointer to the list of methods
            // of the class and the static fields of the class.
            TypeStruct classTypeStruct = Ast.TypeStruct(className + "_Class_Struct",
                    Ast.StructFieldList());
            //Add this Type struct to the list of structs in the LLVM program
            prog.getStructTypes().add(classTypeStruct);

            // Generate a new struct list of class methods.

            StructFieldList classMembersList = Ast.StructFieldList();
            TypeStruct classMethodStruct = Ast.TypeStruct(className + "_Method_Struct",
                    classMembersList);
            prog.getStructTypes().add(classMethodStruct);


            // Make a global constant list for the method struct and add the accesbile methods to
            // it. This is done because the method definitions do not change throughout the
            // execution and the members of the constlist can be easily replaced when overriding.
            ConstList methods = Ast.ConstList();
            Global classGlobalStruct = Ast.Global(classMethodStruct,
                    className + "_Members",
                    true,
                    Ast.ConstStruct(classMethodStruct, methods));
            prog.getGlobals().add(classGlobalStruct);

            // Create a new proc for the implicit constructor of the class.
            // this constructor returns the struct of the class.
            Proc constructor = Ast.Proc(className + "_Constructor",
                    Ast.TypePointer(classTypeStruct),
                    Ast.ParameterList(),
                    Ast.BasicBlockList());

            // Add the class type struct to the translation table
            translationTable.addClassTypeStruct(className, classTypeStruct);
            // Add the constructor to the translation table
            translationTable.addConstructorProc(className, constructor);
            // Add the class member struct to the translation table
            translationTable.addMemberStruct(className, classMethodStruct);
            //Add the global method struct to the translation table
            translationTable.addClassGlobal(className, classGlobalStruct);
            //Add the constructor to the program
            translator.addProcedure(constructor);
        }
        super.visit(classDecl);
    }
}
