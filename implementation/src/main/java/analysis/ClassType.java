package analysis;

import notquitejava.ast.NQJClassDecl;
import notquitejava.ast.NQJExtended;
import notquitejava.ast.NQJExtendsClass;
import notquitejava.ast.NQJExtendsNothing;

/**
 * Class Type implementation. Implements the class hierarchy relations on subtyping.
 */
public class ClassType extends Type {


    private final NQJClassDecl classDecl;

    public ClassType(NQJClassDecl classDecl) {
        this.classDecl = classDecl;
    }

    @Override
    boolean isSubtypeOf(Type other, NameTable nameTable) {
        if (other instanceof ClassType) {
            return isChildOf(
                    this.getClassDecl().getName(),
                    ((ClassType) other).getClassDecl().getName(),
                    nameTable);
        }
        return false;
    }

    @Override
    public String toString() {
        return "class";
    }

    public NQJClassDecl getClassDecl() {
        return this.classDecl;
    }

    /**
     * Checks if one class is in the inheritance hierarchy of the other.
     *
     * @param class1    Parent class
     * @param class2    Child class
     * @param nameTable NameTable containing the class definitions
     * @return True if Child extends the parent. Even transitive inheritance will work.
     */

    public boolean isChildOf(String class1, String class2, NameTable nameTable) {
        NQJClassDecl parent = nameTable.lookupClass(class1);
        NQJClassDecl child = nameTable.lookupClass(class2);
        if (child != null && parent != null) {
            if (child.getName().equals(parent.getName())) {
                return true;
            } else {
                return parent.getExtended().match(new NQJExtended.Matcher<>() {
                    @Override
                    public Boolean case_ExtendsNothing(NQJExtendsNothing extendsNothing) {
                        return false;
                    }

                    @Override
                    public Boolean case_ExtendsClass(NQJExtendsClass extendsClass) {
                        return isChildOf(extendsClass.getName(), class2, nameTable);
                    }
                });
            }

        } else {
            return false;
        }
    }

}






