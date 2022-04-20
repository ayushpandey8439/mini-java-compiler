package translation;

import minillvm.ast.*;

/**
 * Translates the initialisation of types.
 */
public class InitialisationTranslator implements Type.Matcher<Operand> {

    final Operand field;
    final boolean deep;
    final boolean pointer;
    final BasicBlock block;

    InitialisationTranslator(Operand field, boolean deep, boolean pointer,
                             BasicBlock block) {
        this.block = block;
        this.field = field;
        this.deep = deep;
        this.pointer = pointer;
    }


    @Override
    public Operand case_TypeNullpointer(TypeNullpointer typeNullpointer) {
        return Ast.Nullpointer();
    }

    @Override
    public Operand case_TypeInt(TypeInt typeInt) {
        if (this.pointer) {
            // If the field is a primitive type, fetch the value instead of passing a pointer.
            TemporaryVar target = Ast.TemporaryVar(this.field.toString() + "_target");
            block.add(Ast.Load(target, this.field));
            block.add(Ast.Store(this.field.copy(), Ast.ConstInt(0)));
            return Ast.ConstInt(0);
        }
        return this.field;
    }

    @Override
    public Operand case_TypeVoid(TypeVoid typeVoid) {
        return null;
    }

    @Override
    public Operand case_TypeByte(TypeByte typeByte) {
        return null;
    }

    @Override
    public Operand case_TypeArray(TypeArray typeArray) {
        return null;
    }

    @Override
    public Operand case_TypeBool(TypeBool typeBool) {
        if (this.pointer) {
            // If the field is a primitive type, fetch the value instead of passing a pointer.
            TemporaryVar target = Ast.TemporaryVar(this.field.toString() + "_target");
            block.add(Ast.Load(target, this.field.copy()));
            block.add(Ast.Store(this.field.copy(), Ast.ConstBool(false)));
            return Ast.ConstBool(false);
        }
        return this.field;

    }

    @Override
    public Operand case_TypeProc(TypeProc typeProc) {
        return this.field;
    }

    @Override
    public Operand case_TypePointer(TypePointer typePointer) {
        Type targetType = typePointer.getTo();
        targetType.match(new InitialisationTranslator(this.field.copy(),
                this.deep,
                true,
                this.block));
        return this.field;
    }

    @Override
    public Operand case_TypeStruct(TypeStruct typeStruct) {
        if (this.deep) {
            StructFieldList fields = typeStruct.getFields();

            int index = 0;
            for (StructField f : fields) {
                TemporaryVar pointer = Ast.TemporaryVar(f.getName());
                // Initiaise the field. ConstInt(0) is the Base address in the pointer list.
                // index gives the location of the referenced pointer.
                block.add(Ast.GetElementPtr(
                        pointer,
                        this.field.copy(),
                        Ast.OperandList(Ast.ConstInt(0), Ast.ConstInt(index)))
                );
                f.getType().match(new InitialisationTranslator(
                        Ast.VarRef(pointer),
                        false,
                        true,
                        this.block));
                index++;
            }
        }
        return this.field;
    }
}
