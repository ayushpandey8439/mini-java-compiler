//Nested scopes within class methods.
int main(){
    printInt(new A().func2(1,2));
    return 0;
}

class A
{
    int field;
    int func1(int param){
        field = param;
        return field;
    }

    int func2(int field, int param2){
        int x;
        x = this.func1(param2);

        return field;
    }
}