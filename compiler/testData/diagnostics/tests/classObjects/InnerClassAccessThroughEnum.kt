package a


enum class C {
    E1, E2, E3 {
        object O_O

        fun b() {
            O_O
        }

        class G
    },

    E4 {
        fun c() {
            //TODO: this is a bug
            this.<!UNRESOLVED_REFERENCE!>B<!>()

            C.A()
            A()
            //TODO: this is a bug
            this.<!UNRESOLVED_REFERENCE!>A<!>()
        }
    };

    class A
    inner class B
    object O {
        object InO
    }
}

fun f() {
    C.E1.<!NESTED_CLASS_SHOULD_BE_QUALIFIED, FUNCTION_CALL_EXPECTED!>A<!>
    C.E1.<!NESTED_CLASS_SHOULD_BE_QUALIFIED!>A<!>()
    C.E2.B()

    C.E2.<!NESTED_CLASS_SHOULD_BE_QUALIFIED!>O<!>
    C.E3.<!NESTED_CLASS_SHOULD_BE_QUALIFIED!>O<!>.<!NESTED_CLASS_SHOULD_BE_QUALIFIED!>InO<!>

    C.O
    C.O.InO
    C.A()
    C.<!NO_COMPANION_OBJECT, FUNCTION_EXPECTED!>B<!>()

    C.E3.<!UNRESOLVED_REFERENCE!>O_O<!>
    C.E3.<!UNRESOLVED_REFERENCE!>G<!>()
}