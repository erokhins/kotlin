//FILE: a/MyJavaClass.java
package a;

class MyJavaClass {
    static int staticMethod() {
        return 1;
    }

    static class NestedClass {
        static int staticMethodOfNested() {
            return 1;
        }
    }
}

//FILE:a.kt
package a

val mc = MyJavaClass()
val x = MyJavaClass.staticMethod()
val y = MyJavaClass.NestedClass.staticMethodOfNested()
val z = MyJavaClass.NestedClass()

//FILE: b.kt
package b

import a.<!INVISIBLE_REFERENCE!>MyJavaClass<!>

val mc1 = <!UNRESOLVED_REFERENCE!>MyJavaClass<!>()

val x = <!UNRESOLVED_REFERENCE!>MyJavaClass<!>.<!DEBUG_INFO_ELEMENT_WITH_ERROR_TYPE!>staticMethod<!>()
val y = <!UNRESOLVED_REFERENCE!>MyJavaClass<!>.<!DEBUG_INFO_ELEMENT_WITH_ERROR_TYPE!>NestedClass<!>.<!DEBUG_INFO_ELEMENT_WITH_ERROR_TYPE!>staticMethodOfNested<!>()
val z = <!UNRESOLVED_REFERENCE!>MyJavaClass<!>.<!DEBUG_INFO_ELEMENT_WITH_ERROR_TYPE!>NestedClass<!>()

//FILE: c.kt
package a.c

import a.<!INVISIBLE_REFERENCE!>MyJavaClass<!>

val mc1 = <!UNRESOLVED_REFERENCE!>MyJavaClass<!>()

val x = <!UNRESOLVED_REFERENCE!>MyJavaClass<!>.<!DEBUG_INFO_ELEMENT_WITH_ERROR_TYPE!>staticMethod<!>()
val y = <!UNRESOLVED_REFERENCE!>MyJavaClass<!>.<!DEBUG_INFO_ELEMENT_WITH_ERROR_TYPE!>NestedClass<!>.<!DEBUG_INFO_ELEMENT_WITH_ERROR_TYPE!>staticMethodOfNested<!>()
val z = <!UNRESOLVED_REFERENCE!>MyJavaClass<!>.<!DEBUG_INFO_ELEMENT_WITH_ERROR_TYPE!>NestedClass<!>()