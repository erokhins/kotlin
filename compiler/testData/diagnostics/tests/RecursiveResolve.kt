class Test(foo: Any?, bar: Any?) {
    val foo = foo ?: this
    private val bar = bar ?: this
    private val bas = bas()
    val bas2 = bas2()

    private fun bas(): Int = null!!
    private fun bas2(): Int = null!!
}