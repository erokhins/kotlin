trait In<in T>
trait Out<out T>
trait Inv<T>

fun <T> getT(): T = null!!

class Test<in I, out O, P> {
    fun ok1() = getT<O>()
    fun ok2() = getT<In<I>>()
    fun ok3() = getT<In<In<O>>>()
    fun ok4() = getT<Inv<P>>()
    fun ok5() = getT<P>()
    fun ok6() = getT<Out<O>>()
    fun ok7() = getT<P>()
    fun ok8() = getT<Out<In<P>>>()

    <!TYPE_PARAMETER_VARIANCE_CONFLICT(I, in, out, I)!>fun neOk1()<!> = getT<I>()
    <!TYPE_PARAMETER_VARIANCE_CONFLICT(O, out, in, In<O>)!>fun neOk2()<!> = getT<In<O>>()
    <!TYPE_PARAMETER_VARIANCE_CONFLICT(I, in, out, In<In<I>>)!>fun neOk3()<!> = getT<In<In<I>>>()
    <!TYPE_PARAMETER_VARIANCE_CONFLICT(I, in, invariant, Inv<I>)!>fun neOk4()<!> = getT<Inv<I>>()
    <!TYPE_PARAMETER_VARIANCE_CONFLICT(O, out, invariant, Inv<O>)!>fun neOk5()<!> = getT<Inv<O>>()
}