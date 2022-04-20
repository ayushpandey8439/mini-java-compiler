package translation;

import minillvm.ast.*;
import notquitejava.ast.NQJClassDeclList;
import notquitejava.ast.NQJFunctionDecl;

import static minillvm.ast.Ast.VarRef;
import static minillvm.ast.Ast.*;

/**
 * Translates the class method body into llvm IR language.
 */

public class MethodTranslator extends NQJClassDeclList.DefaultVisitor {
    private final Translator tr;
    private final TranslationTable translationTable;

    public MethodTranslator(Translator translator, TranslationTable translationTable) {
        this.tr = translator;
        this.translationTable = translationTable;
    }


    @Override
    public void visit(NQJFunctionDecl functionDecl) {
        Proc proc = tr.loadFunctionProc(functionDecl).get();

        BasicBlock initBlock = proc.getBasicBlocks().get(0);
        tr.setCurrentProc(proc);

        tr.setCurrentBlock(initBlock);

        proc.getParameters().forEach(parameter -> {
            if (!parameter.getName().equals("this")) {
                TemporaryVar param = Ast.TemporaryVar(parameter.getName() + ".reference");
                // Add the definition of the parameter to the function block
                tr.getCurrentBlock().add(Alloca(param, parameter.getType()));
                // Set the reference to the definition of the parameter.
                tr.getCurrentBlock().add(Store(VarRef(param), VarRef(parameter)));
                translationTable.addVariableToScope("var_" + parameter.getName(), param);
            } else {

                translationTable.addVariableToScope(parameter.getName(), parameter);
            }
        });

        translationTable.setCurrentBasicBlockList(proc.getBasicBlocks());
        tr.setCurrentProc(proc);
        tr.translateStmt(functionDecl.getMethodBody());
        translationTable.clearVariableScope();
        super.visit(functionDecl);
    }
}
