package a

class A {
    class Nested
    inner class Inner


    companion object {

        class Nested2

        val c: Int = 1

        object Obj2 {
            val c: Int = 1
        }
    }

    object Obj
}

object O {
    class A

    object O
}

fun f() {
    A.c
    A.hashCode()
    A().<!NO_COMPANION_OBJECT, NESTED_CLASS_ACCESSED_VIA_INSTANCE_REFERENCE!>Nested<!>
    A.Nested()
    A().Inner()
    A.Companion.<!UNRESOLVED_REFERENCE!>Nested<!>
    A.Companion.<!UNRESOLVED_REFERENCE!>Inner<!>
    A.<!NO_COMPANION_OBJECT!>Inner<!>
    A.Companion.c
    A.Companion.Obj2
    A.Companion.Obj2.c

    A.Companion.Nested2()
    A.Companion.c
    A.Obj
    A.Companion.Obj2
    A.<!NESTED_CLASS_SHOULD_BE_QUALIFIED!>Obj2<!>
    A.<!NESTED_CLASS_SHOULD_BE_QUALIFIED!>Obj2<!>.c
    A.<!NESTED_CLASS_SHOULD_BE_QUALIFIED, FUNCTION_CALL_EXPECTED!>Nested2<!>

    O.O
    O.A()
}