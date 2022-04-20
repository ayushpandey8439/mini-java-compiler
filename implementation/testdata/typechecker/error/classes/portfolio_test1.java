//This test should give a type error. Inheritance loops are not allowed.

class b  extends a{

}

class a  extends b{

}

    int main(){
        return 0;
    }
