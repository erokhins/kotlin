trait In<in T>
trait Out<out T>
trait Inv<T>

class Delegate<T> {
    fun get(<!UNUSED_PARAMETER!>t<!>: Any, <!UNUSED_PARAMETER!>p<!>: PropertyMetadata): T = null!!
    fun set(<!UNUSED_PARAMETER!>t<!>: Any, <!UNUSED_PARAMETER!>p<!>: PropertyMetadata, <!UNUSED_PARAMETER!>value<!>: T) {}
}

abstract class Test1<in I, out O, P> {
    val ok1 by Delegate<O>()
    val ok2 by Delegate<In<I>>()
    val ok3 by Delegate<In<In<O>>>()
    val ok4 by Delegate<Inv<P>>()
    val ok5 by Delegate<P>()
    val ok6 by Delegate<Out<O>>()

    var ok7 by Delegate<P>()
    var ok8 by Delegate<Out<In<P>>>()

    <!TYPE_PARAMETER_VARIANCE_CONFLICT(I, in, out, I)!>val neOk1<!> by Delegate<I>()
    <!TYPE_PARAMETER_VARIANCE_CONFLICT(O, out, in, In<O>)!>val neOk2<!> by Delegate<In<O>>()
    <!TYPE_PARAMETER_VARIANCE_CONFLICT(I, in, out, In<In<I>>)!>val neOk3<!> by Delegate<In<In<I>>>()
    <!TYPE_PARAMETER_VARIANCE_CONFLICT(I, in, invariant, Inv<I>)!>val neOk4<!> by Delegate<Inv<I>>()
    <!TYPE_PARAMETER_VARIANCE_CONFLICT(O, out, invariant, Inv<O>)!>val neOk5<!> by Delegate<Inv<O>>()

    <!TYPE_PARAMETER_VARIANCE_CONFLICT(O, out, invariant, O)!>var neOk6<!> by Delegate<O>()
    <!TYPE_PARAMETER_VARIANCE_CONFLICT(I, in, invariant, In<I>)!>var neOk7<!> by Delegate<In<I>>()
    <!TYPE_PARAMETER_VARIANCE_CONFLICT(O, out, invariant, In<In<O>>)!>var neOk8<!> by Delegate<In<In<O>>>()
    <!TYPE_PARAMETER_VARIANCE_CONFLICT(O, out, invariant, Out<O>)!>var neOk9<!> by Delegate<Out<O>>()
}