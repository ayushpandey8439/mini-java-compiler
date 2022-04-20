// This test should give Type error. Assigning a value to the object is not allowed.
int main() {
    int x;
    return 0;
}

class A {
    int z;
    int func() {
        B ob;
        ob = new B();
        int c;
        ob=c;
    }
}

class B{
    int z;
}
