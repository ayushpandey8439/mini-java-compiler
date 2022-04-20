package translation;

import minillvm.ast.*;
import notquitejava.ast.NQJVarDecl;

import java.util.*;

/**
 * Contains the data structures that contain the data used for translation.
 */
public class TranslationTable {
    private final Map<String, TypeStruct> classTypeStructs = new HashMap<>();
    private final Map<String, Proc> classConstructorProcs = new HashMap<>();
    private final Map<String, TypeStruct> classMemberStructs = new HashMap<>();
    private final Map<String, Global> classGlobalStructs = new HashMap<>();
    private final Map<NQJVarDecl, Integer> fieldAddressOffset = new HashMap<>();
    private final Map<String, Integer> methodAddressOffset = new HashMap<>();
    private final Map<String, Variable> variablesInScope = new HashMap<>();

    private BasicBlockList currentBasicBlockList;

    public Optional<TypeStruct> getClassTypeStruct(String className) {
        TypeStruct classTypeStruct = this.classTypeStructs.get(className);
        return classTypeStruct == null ? Optional.empty() : Optional.of(classTypeStruct);
    }

    public void addClassTypeStruct(String className, TypeStruct classStruct) {
        this.classTypeStructs.put(className, classStruct);
    }

    public Optional<Proc> getConstructorProc(String className) {
        Proc proc = this.classConstructorProcs.get(className);
        return proc == null ? Optional.empty() : Optional.of(proc);
    }

    public void addConstructorProc(String className, Proc constructor) {
        this.classConstructorProcs.put(className, constructor);

    }

    public Optional<TypeStruct> getMemberStruct(String className) {
        TypeStruct memberStruct = classMemberStructs.get(className);
        return memberStruct == null ? Optional.empty() : Optional.of(memberStruct);
    }

    public void addMemberStruct(String className, TypeStruct memberStruct) {
        classMemberStructs.put(className, memberStruct);
    }

    public void addClassGlobal(String className, Global global) {
        classGlobalStructs.put(className, global);

    }

    public Optional<Global> getClassGlobal(String className) {
        Global global = classGlobalStructs.get(className);
        return global == null ? Optional.empty() : Optional.of(global);
    }

    int getFieldAddressOffset(NQJVarDecl varDecl) {
        return fieldAddressOffset.get(varDecl);
    }

    void setFieldAddressOffset(NQJVarDecl vardecl, Integer offset) {
        fieldAddressOffset.put(vardecl, offset);
    }

    public void setMethodAddressOffset(String methodName, Integer offset) {
        methodAddressOffset.put(methodName, offset);
    }

    int getMethodAddressOffset(String functionDecl) {
        return methodAddressOffset.get(functionDecl);
    }


    void addVariableToScope(String name, Variable declaration) {
        variablesInScope.put(name, declaration);
    }

    public Optional<Variable> getVariableFromScope(String name) {
        Variable v = variablesInScope.get(name);
        return v == null ? Optional.empty() : Optional.of(v);
    }

    void clearVariableScope() {
        variablesInScope.clear();
    }

    public void setCurrentBasicBlockList(BasicBlockList currentBasicBlockListParam) {
        currentBasicBlockList = currentBasicBlockListParam;
    }

    public BasicBlockList getCurrentBasicBlockList() {
        return currentBasicBlockList;
    }

}
