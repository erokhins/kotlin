package foo

var initOrder = ""

fun nowInit(s: String) {
    if (initOrder != "") {
        initOrder += "_"
    }
    initOrder += s
}

open class B {
    {
        nowInit("B")
    }
    class object {
        {
            nowInit("coB")
        }
    }
}

open class A: B() {
    {
        nowInit("A")
    }
    class object : A() {
        {
            nowInit("coA")
        }
    }
}


fun box(): String {
    A()
    if (initOrder != "coB_B_A_coA_B_A") {
        return "fail: $initOrder"
    }
    return "OK"
}