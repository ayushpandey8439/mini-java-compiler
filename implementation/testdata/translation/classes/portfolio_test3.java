// Indirectly access class fields, getter and setter syntax.
int main(){
        A ob;
        ob = new A();
        ob.set(5);
        printInt(ob.get());
        return 0;
        }

class A {
    int val;

    int get(){
        return val;
    }

    int set(int param){
        val = param;
        return val;
    }
}