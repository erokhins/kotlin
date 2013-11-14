package foo

trait T {
    val bar: String
}

class Foo {
    val ok = "!"
    fun bar(param: String): String {
        val local = "world"
        var a = object {
            val bor = param + local
            val b = object : T {
                override val bar = bor + ok
            }
        }
        return a.b.bar
    }
}

fun box(): Boolean {
    return Foo().bar("hello ") == "hello world!"
}

