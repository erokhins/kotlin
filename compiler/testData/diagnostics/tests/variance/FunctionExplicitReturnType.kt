trait In<in T>
trait Out<out T>
trait Inv<T>

trait Test<in I, out O, P> {
    fun ok1(): O
    fun ok2(): In<I>
    fun ok3(): In<In<O>>
    fun ok4(): Inv<P>
    fun ok5(): P
    fun ok6(): Out<O>
    fun ok7(): P
    fun ok8(): Out<In<P>>

    fun neOk1(): <!TYPE_PARAMETER_VARIANCE_CONFLICT(I, in, out, I)!>I<!>
    fun neOk2(): In<<!TYPE_PARAMETER_VARIANCE_CONFLICT(O, out, in, In<O>)!>O<!>>
    fun neOk3(): In<In<<!TYPE_PARAMETER_VARIANCE_CONFLICT(I, in, out, In<In<I>>)!>I<!>>>
    fun neOk4(): Inv<<!TYPE_PARAMETER_VARIANCE_CONFLICT(I, in, invariant, Inv<I>)!>I<!>>
    fun neOk5(): Inv<<!TYPE_PARAMETER_VARIANCE_CONFLICT(O, out, invariant, Inv<O>)!>O<!>>
}