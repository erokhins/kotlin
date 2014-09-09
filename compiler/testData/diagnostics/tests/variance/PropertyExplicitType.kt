trait In<in T>
trait Out<out T>
trait Inv<T>

fun <T> getT(): T = null!!

trait Test<in I, out O, P> {
    val ok1: O
    val ok2: In<I>
    val ok3: In<In<O>>
    val ok4: Inv<P>
    val ok5: P
    val ok6: Out<O>
    var ok7: P
    var ok8: Out<In<P>>

    val neOk1: <!TYPE_PARAMETER_VARIANCE_CONFLICT(I, in, out, I)!>I<!>
    val neOk2: In<<!TYPE_PARAMETER_VARIANCE_CONFLICT(O, out, in, In<O>)!>O<!>>
    val neOk3: In<In<<!TYPE_PARAMETER_VARIANCE_CONFLICT(I, in, out, In<In<I>>)!>I<!>>>
    val neOk4: Inv<<!TYPE_PARAMETER_VARIANCE_CONFLICT(I, in, invariant, Inv<I>)!>I<!>>
    val neOk5: Inv<<!TYPE_PARAMETER_VARIANCE_CONFLICT(O, out, invariant, Inv<O>)!>O<!>>

    var neOk6: <!TYPE_PARAMETER_VARIANCE_CONFLICT(O, out, invariant, O)!>O<!>
    var neOk7: In<<!TYPE_PARAMETER_VARIANCE_CONFLICT(I, in, invariant, In<I>)!>I<!>>
    var neOk8: In<In<<!TYPE_PARAMETER_VARIANCE_CONFLICT(O, out, invariant, In<In<O>>)!>O<!>>>
    var neOk9: Out<<!TYPE_PARAMETER_VARIANCE_CONFLICT(O, out, invariant, Out<O>)!>O<!>>
}