interface N { fun foo() = 1 }

class WithClassObject {
    companion object {}

    class Nested()
    class NestedWithClassObject { companion object : N }
    enum class NestedEnum { A }
    object NestedObj : N { operator fun invoke() = 1 }
}

class WithoutClassObject {
    class Nested()
    class NestedWithClassObject { companion object : N }
    enum class NestedEnum { A }
    object NestedObj : N { operator fun invoke() = 1 }
}

object Obj {
    class Nested()
    class NestedWithClassObject { companion object : N }
    enum class NestedEnum { A }
    object NestedObj : N { operator fun invoke() = 1 }
}

fun test(with: WithClassObject, without: WithoutClassObject, obj: Obj) {
    with.<!NESTED_CLASS_SHOULD_BE_QUALIFIED!>Nested<!>()
    with.<!NESTED_CLASS_SHOULD_BE_QUALIFIED!>NestedWithClassObject<!>
    with.<!NESTED_CLASS_SHOULD_BE_QUALIFIED!>NestedWithClassObject<!>()
    with.<!NESTED_CLASS_ACCESSED_VIA_INSTANCE_REFERENCE!>NestedWithClassObject<!>.foo()
    with.<!NESTED_CLASS_ACCESSED_VIA_INSTANCE_REFERENCE!>NestedEnum<!>.A
    with.<!NESTED_CLASS_SHOULD_BE_QUALIFIED!>NestedObj<!>
    with.<!NESTED_CLASS_SHOULD_BE_QUALIFIED, FUNCTION_EXPECTED!>NestedObj<!>()
    with.<!NESTED_CLASS_ACCESSED_VIA_INSTANCE_REFERENCE!>NestedObj<!>.foo()

    without.<!NESTED_CLASS_SHOULD_BE_QUALIFIED!>Nested<!>()
    without.<!NESTED_CLASS_SHOULD_BE_QUALIFIED!>NestedWithClassObject<!>
    without.<!NESTED_CLASS_SHOULD_BE_QUALIFIED!>NestedWithClassObject<!>()
    without.<!NESTED_CLASS_ACCESSED_VIA_INSTANCE_REFERENCE!>NestedWithClassObject<!>.foo()
    without.<!NESTED_CLASS_ACCESSED_VIA_INSTANCE_REFERENCE!>NestedEnum<!>.A
    without.<!NESTED_CLASS_SHOULD_BE_QUALIFIED!>NestedObj<!>
    without.<!NESTED_CLASS_SHOULD_BE_QUALIFIED, FUNCTION_EXPECTED!>NestedObj<!>()
    without.<!NESTED_CLASS_ACCESSED_VIA_INSTANCE_REFERENCE!>NestedObj<!>.foo()

    obj.<!NESTED_CLASS_SHOULD_BE_QUALIFIED!>Nested<!>()
    obj.<!NESTED_CLASS_SHOULD_BE_QUALIFIED!>NestedWithClassObject<!>
    obj.<!NESTED_CLASS_SHOULD_BE_QUALIFIED!>NestedWithClassObject<!>()
    obj.<!NESTED_CLASS_ACCESSED_VIA_INSTANCE_REFERENCE!>NestedWithClassObject<!>.foo()
    obj.<!NESTED_CLASS_ACCESSED_VIA_INSTANCE_REFERENCE!>NestedEnum<!>.A
    obj.<!NESTED_CLASS_SHOULD_BE_QUALIFIED!>NestedObj<!>
    obj.<!NESTED_CLASS_SHOULD_BE_QUALIFIED, FUNCTION_EXPECTED!>NestedObj<!>()
    obj.<!NESTED_CLASS_ACCESSED_VIA_INSTANCE_REFERENCE!>NestedObj<!>.foo()
}