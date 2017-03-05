
int fib(int target) {
  if (target == 1) {return 1;}
  if (target == 2) {return 1;}
  return fib(target - 1) + fib(target - 2);
}

int main() {
    int target = 8;

    if (target == 1) {
      puts(1);
    }
    if (target == 2) {
      puts(1);
    }

    if (target > 1) {
      int a = 1;
      int b = 1;
      target = target - 2;
      puts(a);
      while (target > 0) {
        puts(b);
        int tmp = b;
        b = a + b;
        a = tmp;
        target = target - 1;
      }
      puts(b);
    }
}