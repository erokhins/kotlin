trait In<in T>
trait Out<out T>
trait Inv<T>

fun <T> getT(): T = null!!

class Test<in I, out O, P> {
    val ok1 = getT<O>()
    val ok2 = getT<In<I>>()
    val ok3 = getT<In<In<O>>>()
    val ok4 = getT<Inv<P>>()
    val ok5 = getT<P>()
    val ok6 = getT<Out<O>>()
    var ok7 = getT<P>()
    var ok8 = getT<Out<In<P>>>()

    <!TYPE_PARAMETER_VARIANCE_CONFLICT(I, in, out, I)!>val neOk1<!> = getT<I>()
    <!TYPE_PARAMETER_VARIANCE_CONFLICT(O, out, in, In<O>)!>val neOk2<!> = getT<In<O>>()
    <!TYPE_PARAMETER_VARIANCE_CONFLICT(I, in, out, In<In<I>>)!>val neOk3<!> = getT<In<In<I>>>()
    <!TYPE_PARAMETER_VARIANCE_CONFLICT(I, in, invariant, Inv<I>)!>val neOk4<!> = getT<Inv<I>>()
    <!TYPE_PARAMETER_VARIANCE_CONFLICT(O, out, invariant, Inv<O>)!>val neOk5<!> = getT<Inv<O>>()

    <!TYPE_PARAMETER_VARIANCE_CONFLICT(O, out, invariant, O)!>var neOk6<!> = getT<O>()
    <!TYPE_PARAMETER_VARIANCE_CONFLICT(I, in, invariant, In<I>)!>var neOk7<!> = getT<In<I>>()
    <!TYPE_PARAMETER_VARIANCE_CONFLICT(O, out, invariant, In<In<O>>)!>var neOk8<!> = getT<In<In<O>>>()
    <!TYPE_PARAMETER_VARIANCE_CONFLICT(O, out, invariant, Out<O>)!>var neOk9<!> = getT<Out<O>>()
}