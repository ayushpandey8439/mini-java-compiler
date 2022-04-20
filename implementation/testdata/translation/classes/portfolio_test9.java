//Overridden method returns array
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
}

class B extends A{

    int [] AFuncMemb2(int value){
        int[] a;
        a= new int[3];
        a[0] = value;
        return a;
    }

}