int main(){
        A a;
        a = new B();
        printInt(a.function());
        printInt(a.init());
        printInt(a.function());
        return 0;
        }

class A {
    int common;
    int a_member;

    int init() {
        common = 1;
        a_member = 12;
        return common;
    }

    int function() {
        return 8888;
    }

}

class B extends A {
    int common;

    int function() {
        int z;
        z = common + a_member;
        return z;
    }
}
