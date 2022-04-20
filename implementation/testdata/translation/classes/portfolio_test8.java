//Method overriding
int main(){
    B bob;
    bob = new B();
    printInt(bob.AFuncMemb1(12));
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

    int BFuncMemb2(int value){
        AMemb2 = value;
        return AMemb2;
    }

}