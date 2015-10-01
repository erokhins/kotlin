// FILE: A.java
public class A {
    public static void foo() {}
}

// FILE: 1.kt
open class B: A()

// FILE: C.java
public class C extends B {
    public static vod bar() {}
}

// FILE: 2.kt
class D: C() {
    init {
        foo()
        A.foo()
        B.<!UNRESOLVED_REFERENCE!>foo<!>()
        C.<!UNRESOLVED_REFERENCE!>foo<!>() // todo java
        D.<!UNRESOLVED_REFERENCE!>foo<!>()

        bar()
        C.bar()
        D.<!UNRESOLVED_REFERENCE!>bar<!>()
    }
}