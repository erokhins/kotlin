package bar

fun main(args : Array<String>) {
    class Some

    <!DEBUG_INFO_MISSING_UNRESOLVED!><!NO_COMPANION_OBJECT!>Some<!>[<!SYNTAX!><!>]<!> <!DEBUG_INFO_MISSING_UNRESOLVED!>names<!> <!DEBUG_INFO_MISSING_UNRESOLVED!><!SYNTAX!>=<!> ["ads"]<!>
}