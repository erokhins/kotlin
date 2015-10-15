// !DIAGNOSTICS: -UNUSED_EXPRESSION
import A.Inner

class A {
    inner class Inner
}

fun main() {
    ::<!UNSUPPORTED, CALLABLE_REFERENCE_TO_MEMBER_OR_EXTENSION_WITH_EMPTY_LHS!>Inner<!>
}