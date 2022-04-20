package analysis;

import notquitejava.ast.*;

/**
 * Implements the Type matcher to convert NQJ Types to Explicit types.
 */
public class TypeMatcher implements NQJType.Matcher<Type> {

    private final Analysis analysis;
    private final TypeContext ctxt;
    private final NameTable nameTable;
    /**
     * Initialise the Type matcher.
     * */

    public TypeMatcher(Analysis analysis, TypeContext ctxt, NameTable nameTable) {
        this.analysis = analysis;
        this.ctxt = ctxt;
        this.nameTable = nameTable;
    }

    @Override
    public Type case_TypeInt(NQJTypeInt typeInt) {
        return Type.INT;
    }

    @Override
    public Type case_TypeBool(NQJTypeBool typeBool) {
        return Type.BOOL;
    }

    @Override
    public Type case_TypeClass(NQJTypeClass typeClass) {
        NQJClassDecl classDecl = nameTable.lookupClass(typeClass.getName());
        if (classDecl == null) {
            return Type.NULL;
        } else {
            return new ClassType(classDecl);
        }
    }

    @Override
    public Type case_TypeArray(NQJTypeArray typeArray) {
        return new ArrayType(typeArray.getComponentType()
                .match(new TypeMatcher(this.analysis, this.ctxt, this.nameTable)));
    }
}
