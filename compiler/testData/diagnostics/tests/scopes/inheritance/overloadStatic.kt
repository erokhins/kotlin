// FILE: A.java
class A {
    static void foo() {}
}

// FILE: B.java
class B extends A {
    static void foo() {}
}

// FILE: 1.kt
class E: B() {
    init {
        <!OVERLOAD_RESOLUTION_AMBIGUITY!>foo<!>() // todo
    }
}