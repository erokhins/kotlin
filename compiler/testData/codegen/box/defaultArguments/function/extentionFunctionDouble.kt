fun Double.foo(a: Double = 1.0): Double {
    return a
}

fun box(): String  {
    if (1.0.foo() != 1.0) return "fail"
    if (1.0.foo(2.0) != 2.0) return "fail"
    return "OK"
}

