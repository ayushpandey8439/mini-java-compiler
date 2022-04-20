//Test member inheritance between classes.
int main(){
    A aob;
    aob = new A();
    B bob;
    bob = new B();

    printInt(aob.AFuncMemb1(5));
    printInt(bob.BFuncMemb2(12));
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
    int BFuncMemb1(int value){
        AMemb1 = value;
        return AMemb1;
    }
    int BFuncMemb2(int value){
        AMemb2 = value;
        return AMemb2;
    }
}