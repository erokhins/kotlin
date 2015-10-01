// FILE: A.java
class A {
    static void foo() {}
    static void bar() {}
}

// FILE: B.java
class B extends A {}

// FILE: C.java
class C {
    static void bar() {}
}

// FILE: 1.kt
import A.foo
import B.<!UNRESOLVED_REFERENCE!>bar<!> // todo

class E: A() {
    init {
        foo()
        bar()
    }
}

class F: B() {
    init {
        foo()
        bar()
    }
}

// FILE: 2.kt
import C.bar

class Z: A() {
    init {
        <!OVERLOAD_RESOLUTION_AMBIGUITY!>bar<!>() //todo
    }
}

// FILE: 3.kt
import C.*

class Q: A() {
    init {
        <!OVERLOAD_RESOLUTION_AMBIGUITY!>bar<!>() // todo
    }
}