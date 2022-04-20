// Creates a new object for a class and access a method with parameters.
int main(){
        printInt(new A().func(3,5));
        return 0;
        }

class A {
    int func(int p1, int p2){
        int b;
        b=p2;
        printInt(p1);
        return b;
    }
}