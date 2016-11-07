fun <X> listOf(): List<X> = TODO()

fun <Y> foo(f: () -> List<Y>): Y = TODO()

fun test(): String = foo { listOf() }
