//This should give a type error. Using 'this' outside of a class context is not allowed.
int main(){

        int c;
        c = this.x;
        return 0;
        }

class A{
    int x;
}
