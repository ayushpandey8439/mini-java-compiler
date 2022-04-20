//This test should give a type error. Duplicate method declarations are not allowed.

class a{
    int a(int[] a){
        printInt(12);
    }
    int a(int[] a){
        printInt(12);
    }
}


int main(){
    return 0;
}