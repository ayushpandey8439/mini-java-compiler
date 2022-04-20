package analysis;

/**
 * Type class for handling the formal types of NotQuiteJava and the sub-type relation.
 * Provides static members for basic types.
 */
public abstract class Type {

    abstract boolean isSubtypeOf(Type other, NameTable nameTable);

    public boolean isEqualToType(Type other, NameTable nameTable) {
        return this.isSubtypeOf(other, nameTable) && other.isSubtypeOf(this, nameTable);
    }


    public static final Type INT = new Type() {
        @Override
        boolean isSubtypeOf(Type other, NameTable nameTable) {
            return other == this || other == ANY;
        }


        @Override
        public String toString() {
            return "int";
        }
    };

    public static final Type BOOL = new Type() {

        @Override
        boolean isSubtypeOf(Type other, NameTable nameTable) {
            return other == this || other == ANY;
        }

        @Override
        public String toString() {
            return "boolean";
        }
    };

    public static final Type NULL = new Type() {

        @Override
        boolean isSubtypeOf(Type other, NameTable nameTable) {
            return other == this
                    || other instanceof ArrayType
                    || other == ANY;
        }

        @Override
        public String toString() {
            return "null";
        }
    };

    public static final Type INVALID = new Type() {

        @Override
        boolean isSubtypeOf(Type other, NameTable nameTable) {
            return false;
        }

        @Override
        public String toString() {
            return "invalid type";
        }
    };

    public static final Type ANY = new Type() {

        @Override
        boolean isSubtypeOf(Type other, NameTable nameTable) {
            return true;
        }

        @Override
        public String toString() {
            return "any";
        }
    };


}
