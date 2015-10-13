open class SomeClass<T>
class TestSome<P> {
    companion object : SomeClass<<!UNRESOLVED_REFERENCE!>P<!>>() {
    }
}

class Test {
    companion object : <!UNRESOLVED_REFERENCE, DEBUG_INFO_UNRESOLVED_WITH_TARGET!>InnerClass<!>() {
        val a = object: <!UNRESOLVED_REFERENCE, DEBUG_INFO_UNRESOLVED_WITH_TARGET!>InnerClass<!>() {
        }

        fun more(): InnerClass {
            val b = <!INACCESSIBLE_OUTER_CLASS_EXPRESSION!>InnerClass()<!>

            val <!UNUSED_VARIABLE!>testVal<!> = <!UNRESOLVED_REFERENCE!>inClass<!>
            <!UNRESOLVED_REFERENCE!>foo<!>()

            return b
        }
    }

    val inClass = 12
    fun foo() {}

    open inner class InnerClass
}