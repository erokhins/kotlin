// FILE: a.kt
package outer

private fun a() {}
private class B

// FILE: b.kt
package outer.p1

import outer.<!INVISIBLE_REFERENCE!>a<!>

fun use() {
    <!UNRESOLVED_REFERENCE!>a<!>()
    outer.<!INVISIBLE_MEMBER!>B<!>()
}

// FILE: c.kt
package outer.p1.p2

import outer.<!INVISIBLE_REFERENCE!>a<!>

fun use() {
    <!UNRESOLVED_REFERENCE!>a<!>()
    outer.<!INVISIBLE_MEMBER!>B<!>()
}