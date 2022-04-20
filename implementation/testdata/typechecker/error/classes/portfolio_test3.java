//This test should give a type error. Incorrect overriding of method in the child class.

class b  extends a{
    int func(int param){
        printInt(param);
    }
}

class a  {
    boolean func(int param){
        printInt(param);
    }
}

    int main(){
        return 0;
    }