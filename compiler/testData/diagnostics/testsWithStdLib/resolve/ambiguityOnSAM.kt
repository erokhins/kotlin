// !CHECK_TYPE
// FILE: A.java
import kotlin.Function;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;

public class A {
    public interface MyRunnable {
        void run();
    }

    public char foo1(Object o) { return '4'; }
    public String foo1(Runnable r) { return ""; }
    public int foo1(MyRunnable c) { return 4; }

    public char foo2(Object o) { return '4'; }
    public String foo2(Runnable r) { return ""; }
    public int foo2(Function0<Unit> c) { return 4; }

    public char foo3(Object o) { return '4'; }
    public String foo3(Runnable r) { return ""; }
    public int foo3(Function<Unit> c) { return 4; }
}



// FILE: 1.kt
fun test1(a: A, r: Runnable, v: A.MyRunnable, o: Any) {
    a.foo1 {  } checkType { _<Char>() }

    a.foo1(r) checkType { _<String>() }
    a.foo1(v) checkType { _<Int>() }
    a.foo1(o) checkType { _<Char>() }
}

fun test2(a: A, r: Runnable, v: () -> Unit, o: Any) {
    a.foo2 { } checkType { _<Int>() }

    a.foo2(r) checkType { _<String>() }
    a.foo2(v) checkType { _<Int>() }
    a.foo2(o) checkType { _<Char>() }
}

fun test3(a: A, r: Runnable, v: Function<Unit>, o: Any) {
    a.foo3 { } checkType { _<String>() }

    a.foo3(r) checkType { _<String>() }
    a.foo3(v) checkType { _<Int>() }
    a.foo3(o) checkType { _<Char>() }
}

