//Make function return class type.
int main(){
    A aob;
    aob = new B().create();

    printInt(aob.AFuncMemb1(5));
    return 0;
}

class A
{
    int AMemb1;
    int AMemb2;
     int AFuncMemb1(int value){
         AMemb1 = value;
         return AMemb1;
     }
    int AFuncMemb2(int value){
        AMemb2 = value;
        return AMemb2;
    }
}

class B extends A{
    A create(){
        return new A();
    }
}