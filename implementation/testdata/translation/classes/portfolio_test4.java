//Fibonacci Series using Recursion
int main(){
    int n;
    n=9;
    fibonacci f;
    f = new fibonacci();
    printInt(f.fib(n));
    return 0;
}

class fibonacci
{
    int fib(int n)
    {
        int result;
        if (n < 2){
            result= n;
        }
        else{
            result =  (this.fib(n-1) + this.fib(n-2));
        }

        return result;
    }
} 