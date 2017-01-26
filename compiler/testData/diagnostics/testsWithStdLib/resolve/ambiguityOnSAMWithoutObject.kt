// !CHECK_TYPE
// FILE: A.java
import kotlin.Function;
import kotlin.jvm.functions.Function1;

public class A {
    public interface MyRunnable {
        void run();
    }

    public String foo1(Runnable r) { return ""; }
    public int foo1(MyRunnable c) { return 4; }

    public String foo2(Runnable r) { return ""; }
    public int foo2(Function1<String, Integer> c) { return 4; }

    public String foo3(Runnable r) { return ""; }
    public int foo3(Function<String> c) { return 4; }
}



// FILE: 1.kt
fun test1(a: A, r: Runnable, v: A.MyRunnable) {
    a.<!OVERLOAD_RESOLUTION_AMBIGUITY!>foo1<!> {  }

    a.foo1(r) checkType { _<String>() }
    a.foo1(v) checkType { _<Int>() }
}

fun test2(a: A, r: Runnable, v: (String) -> Int) {
    a.foo2 { 4 } checkType { _<Int>() }

    a.foo2(r) checkType { _<String>() }
    a.foo2(v) checkType { _<Int>() }
}

fun test3(a: A, r: Runnable, v: Function<String>) {
    a.foo3 { "" } checkType { _<Int>() }

    a.foo3(r) checkType { _<String>() }
    a.foo3(v) checkType { _<Int>() }
}
