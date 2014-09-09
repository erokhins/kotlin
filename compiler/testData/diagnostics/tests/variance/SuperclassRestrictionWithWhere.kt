trait In<in T>
trait Out<out T>
trait Inv<T>

fun <T> getT(): T = null!!

trait Test2<in I, out O, P> {
    val <X>  ok1: P where X : I
    val <X>  ok2: P where X : In<O>
    val <X>  ok3: P where X : In<In<I>>
    val <X>  ok4: P where X : Inv<P>
    val <X>  ok5: P where X : P
    val <X>  ok6: P where X : Out<I>
    var <X>  ok7: P where X : Out<Out<I>>
    var <X>  ok8: P where X : Out<In<O>>
    var <X>  ok9: P where X : Out<In<P>>

    val <X>  neOk1: P where X : <!TYPE_PARAMETER_VARIANCE_CONFLICT(O, out, in, O)!>O<!>
    val <X>  neOk2: P where X : In<<!TYPE_PARAMETER_VARIANCE_CONFLICT(I, in, out, In<I>)!>I<!>>
    val <X>  neOk3: P where X : In<In<<!TYPE_PARAMETER_VARIANCE_CONFLICT(O, out, in, In<In<O>>)!>O<!>>>
    val <X>  neOk4: P where X : Inv<<!TYPE_PARAMETER_VARIANCE_CONFLICT(I, in, invariant, Inv<I>)!>I<!>>
    val <X>  neOk5: P where X : Inv<<!TYPE_PARAMETER_VARIANCE_CONFLICT(O, out, invariant, Inv<O>)!>O<!>>
    var <X>  neOk6: P where X : In<In<<!TYPE_PARAMETER_VARIANCE_CONFLICT(O, out, in, In<In<O>>)!>O<!>>>

    var <X>  Ok10: P where X : I
    var <X>  Ok11: P where X : In<O>
    var <X>  Ok12: P where X : In<In<I>>
    var <X>  Ok13: P where X : Out<I>
}