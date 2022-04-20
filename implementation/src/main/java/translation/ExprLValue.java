package translation;

import minillvm.ast.*;
import notquitejava.ast.*;

import java.util.Optional;

/**
 * Evaluate L values.
 */
public class ExprLValue implements NQJExprL.Matcher<Operand> {
    private final Translator tr;
    private final TranslationTable translationTable;

    public ExprLValue(Translator translator, TranslationTable translationTable) {
        this.tr = translator;
        this.translationTable = translationTable;
    }

    @Override
    public Operand case_ArrayLookup(NQJArrayLookup e) {
        Operand arrayAddr = tr.exprRvalue(e.getArrayExpr());
        tr.addNullcheck(arrayAddr, "Nullpointer exception in line " + tr.sourceLine(e));

        Operand index = tr.exprRvalue(e.getArrayIndex());

        Operand len = tr.getArrayLen(arrayAddr);
        TemporaryVar smallerZero = Ast.TemporaryVar("smallerZero");
        TemporaryVar lenMinusOne = Ast.TemporaryVar("lenMinusOne");
        TemporaryVar greaterEqualLen = Ast.TemporaryVar("greaterEqualLen");
        TemporaryVar outOfBoundsV = Ast.TemporaryVar("outOfBounds");
        final BasicBlock outOfBounds = tr.newBasicBlock("outOfBounds");
        final BasicBlock indexInRange = tr.newBasicBlock("indexInRange");


        // smallerZero = index < 0
        tr.addInstruction(Ast.BinaryOperation(smallerZero, index, Ast.Slt(), Ast.ConstInt(0)));
        // lenMinusOne = length - 1
        tr.addInstruction(Ast.BinaryOperation(lenMinusOne, len, Ast.Sub(), Ast.ConstInt(1)));
        // greaterEqualLen = lenMinusOne < index
        tr.addInstruction(Ast.BinaryOperation(greaterEqualLen,
                Ast.VarRef(lenMinusOne), Ast.Slt(), index.copy()));
        // outOfBoundsV = smallerZero || greaterEqualLen
        tr.addInstruction(Ast.BinaryOperation(outOfBoundsV,
                Ast.VarRef(smallerZero), Ast.Or(), Ast.VarRef(greaterEqualLen)));

        tr.getCurrentBlock().add(Ast.Branch(Ast.VarRef(outOfBoundsV), outOfBounds, indexInRange));

        tr.addBasicBlock(outOfBounds);
        outOfBounds.add(Ast.HaltWithError("Index out of bounds error in line " + tr.sourceLine(e)));

        tr.addBasicBlock(indexInRange);
        tr.setCurrentBlock(indexInRange);
        TemporaryVar indexAddr = Ast.TemporaryVar("indexAddr");
        tr.addInstruction(Ast.GetElementPtr(indexAddr, arrayAddr, Ast.OperandList(
                Ast.ConstInt(0),
                Ast.ConstInt(1),
                index.copy()
        )));
        return Ast.VarRef(indexAddr);
    }

    @Override
    public Operand case_FieldAccess(NQJFieldAccess e) {
        Operand receiver = tr.exprRvalue(e.getReceiver());
        BasicBlock currentBlock = tr.getCurrentBlock();
        TemporaryVar fieldPointer = Ast.TemporaryVar("fieldPointer");
        int fieldOffset = translationTable.getFieldAddressOffset(e.getVariableDeclaration());
        currentBlock.add(Ast.GetElementPtr(
                fieldPointer,
                receiver,
                Ast.OperandList(Ast.ConstInt(0), Ast.ConstInt(fieldOffset))
        ));

        return Ast.VarRef(fieldPointer);
    }

    @Override
    public Operand case_VarUse(NQJVarUse e) {
        BasicBlock currentBlock = tr.getCurrentBlock();
        Optional<Variable> scopeReference = translationTable
                .getVariableFromScope("var_" + e.getVarName());

        if (scopeReference.isEmpty()) {
            int addressOffset = translationTable.getFieldAddressOffset(e.getVariableDeclaration());
            TemporaryVar scopeVariable = Ast.TemporaryVar(e.getVarName() + "_scope_reference");

            currentBlock.add(Ast.GetElementPtr(
                    scopeVariable,
                    Ast.VarRef(translationTable.getVariableFromScope("this").get()),
                    Ast.OperandList(Ast.ConstInt(0), Ast.ConstInt(addressOffset))
            ));
            scopeReference = Optional.of(scopeVariable);
        }
        // local TemporaryVar
        return Ast.VarRef(scopeReference.get());
    }

}
