// "Add 'kotlin.Any' as upper bound for E" "false"
// ACTION: Convert to block body
// ACTION: Introduce local variable

inline fun <reified /* abc */   E> bar() = E::class.java<caret>
