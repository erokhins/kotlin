interface A {
    val foo: String
}

fun test(foo: Int) {
    object : A {
        override val foo: Int = foo
    }
}