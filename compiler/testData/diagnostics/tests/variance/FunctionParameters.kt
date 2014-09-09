trait In<in T>
trait Out<out T>
trait Inv<T>

trait Test<in I, out O, P> {
    fun ok1(i: I)
    fun ok2(i: In<O>)
    fun ok3(i: In<In<I>>)
    fun ok4(i: Inv<P>)
    fun ok5(i: P)
    fun ok6(i: Out<I>)
    fun ok7(i: Out<Out<I>>)
    fun ok8(i: Out<In<O>>)
    fun ok9(i: Out<In<P>>)

    fun neOk1(i: <!TYPE_PARAMETER_VARIANCE_CONFLICT(O, out, in, O)!>O<!>)
    fun neOk2(i: In<<!TYPE_PARAMETER_VARIANCE_CONFLICT(I, in, out, In<I>)!>I<!>>)
    fun neOk3(i: In<In<<!TYPE_PARAMETER_VARIANCE_CONFLICT(O, out, in, In<In<O>>)!>O<!>>>)
    fun neOk4(i: Inv<<!TYPE_PARAMETER_VARIANCE_CONFLICT(I, in, invariant, Inv<I>)!>I<!>>)
    fun neOk5(i: Inv<<!TYPE_PARAMETER_VARIANCE_CONFLICT(O, out, invariant, Inv<O>)!>O<!>>)
    fun neOk6(i: In<In<<!TYPE_PARAMETER_VARIANCE_CONFLICT(O, out, in, In<In<O>>)!>O<!>>>)

    fun Ok10(i: I)
    fun Ok11(i: In<O>)
    fun Ok12(i: In<In<I>>)
    fun Ok13(i: Out<I>)
}