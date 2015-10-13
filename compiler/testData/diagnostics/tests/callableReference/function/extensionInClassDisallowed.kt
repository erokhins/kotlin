// !DIAGNOSTICS: -UNUSED_EXPRESSION
class A {
    fun Int.extInt() = 42
    fun A.extA(x: String) = x
    
    fun main() {
        Int::<!UNRESOLVED_REFERENCE_WRONG_RECEIVER!>extInt<!>
        A::<!EXTENSION_IN_CLASS_REFERENCE_NOT_ALLOWED!>extA<!>
    }
}

fun main() {
    A::<!UNRESOLVED_REFERENCE!>extInt<!>
    A::<!UNRESOLVED_REFERENCE!>extA<!>
}
