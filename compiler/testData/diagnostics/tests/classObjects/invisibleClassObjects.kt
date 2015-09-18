//FILE:a.kt
package a

class A {
    companion object {
        fun foo() {}
    }
}

private class B {
    companion object {
        fun bar() {}
    }
}

class C {
    private companion object {
        fun baz() {}
    }
}

private class D {
    private companion object {
        fun quux() {}
    }
}

//FILE:b.kt
package b

import a.A
import a.<!INVISIBLE_REFERENCE!>B<!>
import a.C
import a.<!INVISIBLE_REFERENCE!>D<!>

fun test() {
    f(A)
    f(<!UNRESOLVED_REFERENCE!>B<!>)
    f(<!INVISIBLE_MEMBER!>C<!>)
    f(<!UNRESOLVED_REFERENCE!>D<!>)

    A.foo()
    <!UNRESOLVED_REFERENCE!>B<!>.<!DEBUG_INFO_ELEMENT_WITH_ERROR_TYPE!>bar<!>()
    C.<!INVISIBLE_MEMBER!>baz<!>()
    <!UNRESOLVED_REFERENCE!>D<!>.<!DEBUG_INFO_ELEMENT_WITH_ERROR_TYPE!>quux<!>()

    a.A.foo()
    a.C.<!INVISIBLE_MEMBER!>baz<!>()
}

fun f(<!UNUSED_PARAMETER!>unused<!>: Any) {}