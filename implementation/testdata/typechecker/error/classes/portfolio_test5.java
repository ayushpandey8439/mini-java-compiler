//This test gives a type error. the function BFunc expects ob to be of type B while the function 'func' in class 'A' provides an integer which it accesses from class B.
int main() {
        int x;
        return 0;
        }

class A {
    int func(){
        int[] a;
        boolean b;
        B ob;
        ob = new B();
        ob.BFunc(a,ob.z);
    }
}

class B extends C{
    int z;
}

class C{
    int BFunc(int[] a, B ob){}
}