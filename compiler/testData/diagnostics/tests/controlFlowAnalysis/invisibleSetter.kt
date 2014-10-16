package a

class P<T> {
    var x : T = null
        private set

    var y : T = null

    val other = P<T>();

    {
        x = null
        other.x = null
    }

    val testInGetter : T
        get() {
            x = null
            return x
        }
}

fun foo() {
    val p = P<Int>()
    <!INVISIBLE_SETTER!>p.x<!> = 34 //should be an error here
    p.y = 23

    fun inner() {
        <!INVISIBLE_SETTER!>p.x<!> = 44
    }
}

class R {
    val p = P<Int>();
    {
        <!INVISIBLE_SETTER!>p.x<!> = 42
    }

    val testInGetterInOtherClass : Int
        get() {
            <!INVISIBLE_SETTER!>p.x<!> = 33
            return 3
        }
}