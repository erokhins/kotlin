interface A<T> {
    fun foo(t: T, u: Int) = "A"
}

interface B<T, U> {
    fun foo(t: T, u: U) = "B"
}

class Z1 : A<String>, B<String, Int> {
    override fun foo(t: String, u: Int) = "Z1"
}

class Z2 : B<String, Int>, A<String> {
    override fun foo(t: String, u: Int) = "Z2"
}

fun box(): String {
    val z1 = Z1()
    val z2 = Z2()
    val z1a: A<String> = z1
    val z1b: B<String, Int> = z1
    val z2a: A<String> = z2
    val z2b: B<String, Int> = z2
    return when {
        z1.foo("", 0)  != "Z1" -> "Fail #1"
        z1a.foo("", 0) != "Z1" -> "Fail #2"
        z1b.foo("", 0) != "Z1" -> "Fail #3"
        z2.foo("", 0)  != "Z2" -> "Fail #4"
        z2a.foo("", 0) != "Z2" -> "Fail #5"
        z2b.foo("", 0) != "Z2" -> "Fail #6"
        else -> "OK"
    }
}


