package foo

// NOTE THIS FILE IS AUTO-GENERATED by the generateTestDataForReservedWords.kt. DO NOT EDIT!

class TestClass {
    val with: Int = 0

    fun test() {
        testNotRenamed("with", { with })
    }
}

fun box(): String {
    TestClass().test()

    return "OK"
}