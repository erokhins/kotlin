package foo

// NOTE THIS FILE IS AUTO-GENERATED by the generateTestDataForReservedWords.kt. DO NOT EDIT!

class TestClass {
    var `super`: Int = 0

    fun test() {
        testNotRenamed("super", { `super` })
    }
}

fun box(): String {
    TestClass().test()

    return "OK"
}