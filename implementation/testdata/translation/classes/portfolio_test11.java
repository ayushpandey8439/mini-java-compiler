//Shadowing the variable a from class A in class B.
int main(){
        A ob;
        ob = new A();
        printInt(ob.a);
        B ob1;
        ob1 = new B();
        printInt(ob1.a);
        return 0;
        }
class A{
    int a;
    int setA(){
        int a;
        a=5;
        return a;
    }
}
class B extends A{
    int a;
}