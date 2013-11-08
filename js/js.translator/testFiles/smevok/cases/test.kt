package foo.bar

trait T {
    class object {
        val a = 1
    }
}

class A : T {
    val a = 3
    class object {
        val a = 2
    }
}

fun foo(): Int = 4

public fun box(): String {
    if (T.a != 1) return "T.a != 1, it: ${T.a}"
    if (A.a != 2) return "A.a != 2, it: ${A.a}"
    if (A().a != 3) return "A().a != 3, it: ${A().a}"
    if (foo() != 4) return "foo() != 4, it: ${foo()}"
    A()
    A.a
    foo()
    return  "OK"
}