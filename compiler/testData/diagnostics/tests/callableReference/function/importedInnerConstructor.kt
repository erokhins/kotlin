// !DIAGNOSTICS: -UNUSED_EXPRESSION
import A.Inner

class A {
    inner class Inner
}

fun main() {
    ::<!UNSUPPORTED!>Inner<!>
}