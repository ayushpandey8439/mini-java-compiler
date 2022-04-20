//This test should give a typ error. the definition for object 'ob' needs class B which is not defined.
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
        ob.BFunc(a,ob);
    }
}