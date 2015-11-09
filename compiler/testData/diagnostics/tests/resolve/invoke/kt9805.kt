class A {
    val foo: B.() -> Unit get() = null!!
}

class B

fun test(a: A, b: B) {
    with(b) {
        a.<!DEPRECATED_INVOKE_EXTENSION_WITH_BOTH_RECEIVERS!>foo<!>() // here must be error, because a is not extension receiver

        a.foo(this)

        (a.foo)()

        (a.foo)(this)
    }
}

public inline fun <T, R> with(receiver: T, f: T.() -> R): R = receiver.f()
