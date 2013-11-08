package foo

enum class A {
    OK
}

fun box(): String {
    return A.OK.name()
}