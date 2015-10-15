interface A {
    val foo: Int
    val bar: String
        get() = ""
}

fun test(<!UNUSED_PARAMETER!>foo<!>: Int, <!UNUSED_PARAMETER!>bar<!>: Int) {
    object : A {
        override val foo: Int = <!UNINITIALIZED_VARIABLE!>foo<!> <!NONE_APPLICABLE!>+<!> bar
    }
}