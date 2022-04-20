package analysis;

import java.util.*;

import notquitejava.ast.*;

/**
 * Name table for analysis class hierarchies.
 */
public class NameTable {
    private final Map<Type, ArrayType> arrayTypes = new HashMap<>();

    private final Map<String, NQJFunctionDecl> globalFunctions = new HashMap<>();

    private final Map<String, NQJClassDecl> classes = new HashMap<>();
    private final NQJClassDeclList classDeclList;

    NameTable(Analysis analysis, NQJProgram prog) {
        globalFunctions.put("printInt", NQJ.FunctionDecl(NQJ.TypeInt(), "main",
                NQJ.VarDeclList(NQJ.VarDecl(NQJ.TypeInt(), "elem")), NQJ.Block()));
        for (NQJFunctionDecl f : prog.getFunctionDecls()) {
            var old = globalFunctions.put(f.getName(), f);
            if (old != null) {
                analysis.addError(f, "There already is a global function with name " + f.getName()
                        + " defined in " + old.getSourcePosition());
            }
        }

        this.classDeclList = prog.getClassDecls();

        for (NQJClassDecl classDecl : prog.getClassDecls()) {
            var old = classes.put(classDecl.getName(), classDecl);
            if (old != null) {
                analysis.addError(classDecl, "Duplicate class declaration found.");
            }
        }

    }

    public NQJFunctionDecl lookupFunction(String functionName) {
        return globalFunctions.get(functionName);
    }

    public NQJClassDecl lookupClass(String className) {
        return classes.get(className);
    }

    public NQJClassDeclList getClassDefinitions() {
        return this.classDeclList;
    }

    /**
     * Fetch the function definition from the class hierarchy.
     */
    public Optional<NQJFunctionDecl> getMethodFromClassHierarchy(
            String className,
            String methodName
    ) {
        NQJClassDecl classDecl = classes.get(className);
        if (classDecl != null) {
            Optional<NQJFunctionDecl> methodDecl = classDecl.getMethods()
                    .stream()
                    .filter(method -> method.getName().equals(methodName)).findAny();
            if (methodDecl.isEmpty()) {
                return classDecl.getExtended()
                        .match(new NQJExtended.Matcher<Optional<NQJFunctionDecl>>() {
                            @Override
                            public Optional<NQJFunctionDecl> case_ExtendsNothing(
                                    NQJExtendsNothing extendsNothing
                            ) {
                                return Optional.empty();
                            }

                            @Override
                            public Optional<NQJFunctionDecl> case_ExtendsClass(
                                    NQJExtendsClass extendsClass
                            ) {
                                return getMethodFromClassHierarchy(
                                        extendsClass.getName(),
                                        methodName
                                );
                            }
                        });

            }

            return methodDecl;
        }
        return Optional.empty();
    }

    /**
     * Return a variable declaration from a class.
     */

    public Optional<NQJVarDecl> getFieldFromClass(String className, String fieldName) {
        NQJClassDecl classDecl = classes.get(className);
        if (classDecl != null) {
            Optional<NQJVarDecl> fieldDecl = classDecl.getFields()
                    .stream()
                    .filter(field -> field.getName().equals(fieldName)).findAny();
            if (fieldDecl.isEmpty()) {
                return classDecl.getExtended()
                        .match(new NQJExtended.Matcher<Optional<NQJVarDecl>>() {
                            @Override
                            public Optional<NQJVarDecl> case_ExtendsNothing(
                                    NQJExtendsNothing extendsNothing
                            ) {
                                return Optional.empty();
                            }

                            @Override
                            public Optional<NQJVarDecl> case_ExtendsClass(
                                    NQJExtendsClass extendsClass
                            ) {
                                return getFieldFromClass(extendsClass.getName(), fieldName);
                            }
                        });
            }
            return fieldDecl;
        }
        return Optional.empty();
    }

    /**
     * Transform base type to array type.
     */
    public ArrayType getArrayType(Type baseType) {
        if (!arrayTypes.containsKey(baseType)) {
            arrayTypes.put(baseType, new ArrayType(baseType));
        }
        return arrayTypes.get(baseType);
    }
}
