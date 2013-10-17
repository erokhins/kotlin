package foo

var initOrder = ""

fun nowInit(s: String) {
    if (initOrder != "") {
        initOrder += "_"
    }
    initOrder += s
}

open class A {
    {
        nowInit("A")
    }
    class object {
        val a = 1
        {
            nowInit("coA")
        }
    }
}

trait T : A {
    class object {
        val a = 1
        {
            nowInit("coT")
        }
    }
}

open class B : A() {
    {
        nowInit("B")
    }
    class object {
        val a = 1
        {
            nowInit("coB")
        }
    }
}

open class C : B(), T {
    {
        nowInit("C")
    }
    class object {
        val a = 1
        {
            nowInit("coC")
        }
    }
}

fun box(): String {
    A.a
    B()
    C()
    if (initOrder != "coA_coB_A_B_coC_A_B_C") {
        return "fail: $initOrder"
    }
    return "OK"
}