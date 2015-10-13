// MODULE: m1
// FILE: a.kt
package a.b

class c {
    fun ab_c() {}
}

fun ab_fun() {}

// MODULE: m2
// FILE: b.kt
package a

fun a_fun() {}

class b {
    fun a_b() {}

    class c {
        fun a_bc() {}
    }
}

// FILE: b2.kt
class x {
    fun top_x() {}
}


// MODULE: m3(m1, m2)
// FILE: c.kt
package some

class a {
    class b {
        fun _ab() {}
        class c {
            fun _abc() {}
        }
    }
    fun _a() {}
}

class x {
    fun _x() {}
}

fun test(a_b: package.a.b) {
    a_b.a_b()
    a_b.<!UNRESOLVED_REFERENCE!>_ab<!>()

    package.<!DEBUG_INFO_MISSING_UNRESOLVED!>a<!>.<!DEBUG_INFO_MISSING_UNRESOLVED!>a_fun<!>() // todo

    val a_b2 = package.<!DEBUG_INFO_MISSING_UNRESOLVED!>a<!>.<!DEBUG_INFO_MISSING_UNRESOLVED!>b<!>()
    <!DEBUG_INFO_ELEMENT_WITH_ERROR_TYPE!>a_b2<!>.<!DEBUG_INFO_MISSING_UNRESOLVED!>a_b<!>() // todo
    <!DEBUG_INFO_ELEMENT_WITH_ERROR_TYPE!>a_b2<!>.<!DEBUG_INFO_MISSING_UNRESOLVED!>_ab<!>()

}

fun test2(ab_c: package.a.b.c) {
    ab_c.ab_c()

    val ab_c2 = package.<!DEBUG_INFO_MISSING_UNRESOLVED!>a<!>.<!DEBUG_INFO_MISSING_UNRESOLVED!>b<!>.<!DEBUG_INFO_MISSING_UNRESOLVED!>c<!>()
    <!DEBUG_INFO_ELEMENT_WITH_ERROR_TYPE!>ab_c2<!>.<!DEBUG_INFO_MISSING_UNRESOLVED!>ab_c<!>()
    <!DEBUG_INFO_ELEMENT_WITH_ERROR_TYPE!>ab_c2<!>.<!DEBUG_INFO_MISSING_UNRESOLVED!>a_bc<!>()
}

fun test3(top_x: package.x) {
    top_x.top_x()

    val top_x2 = package.<!DEBUG_INFO_MISSING_UNRESOLVED!>x<!>()
    <!DEBUG_INFO_ELEMENT_WITH_ERROR_TYPE!>top_x2<!>.<!DEBUG_INFO_MISSING_UNRESOLVED!>top_x<!>() // todo
    <!DEBUG_INFO_ELEMENT_WITH_ERROR_TYPE!>top_x2<!>.<!DEBUG_INFO_MISSING_UNRESOLVED!>_x<!>()
}

// FILE: c2.kt
package other

object a {
    object b {
        fun _ab() {}
    }
    fun _a() {}
}


fun test(<!UNUSED_PARAMETER!>a<!>: a) {
    package.<!DEBUG_INFO_MISSING_UNRESOLVED!>a<!>.<!DEBUG_INFO_MISSING_UNRESOLVED!>a_fun<!>() // todo
    package.<!DEBUG_INFO_MISSING_UNRESOLVED!>a<!>.<!DEBUG_INFO_MISSING_UNRESOLVED!>_a<!>() // todo

    package.<!DEBUG_INFO_MISSING_UNRESOLVED!>a<!>.<!DEBUG_INFO_MISSING_UNRESOLVED!>b<!>.<!DEBUG_INFO_MISSING_UNRESOLVED!>ab_fun<!>() // todo
    package.<!DEBUG_INFO_MISSING_UNRESOLVED!>a<!>.<!DEBUG_INFO_MISSING_UNRESOLVED!>b<!>.<!DEBUG_INFO_MISSING_UNRESOLVED!>_ab<!>() // todo
}