package d

//import from objects before properties resolve

import d.<!CANNOT_IMPORT_ON_DEMAND_FROM_SINGLETON!>A<!>.*
import d.M.R
import d.M.<!CANNOT_IMPORT_ON_DEMAND_FROM_SINGLETON!>R<!>.bar
import d.M.T
import d.M.Y

var r: T = T()
val y: T = Y

fun f() {
    <!UNRESOLVED_REFERENCE!>bar<!>()
    R.bar()
    B.foo()
}

object M {
    object R {
        fun bar() {}
    }
    open class T() {}

    object Y : T() {}
}

object A {
    object B {
        fun foo() {}
    }
}