trait In<in T>
trait Out<out T>
trait Inv<T>

fun <T> getT(): T = null!!

trait Test<in I, out O, P> {
    val <X : I> ok1: P
    val <X : In<O>> ok2: P
    val <X : In<In<I>>> ok3: P
    val <X : Inv<P>> ok4: P
    val <X : P> ok5: P
    val <X : Out<I>> ok6: P

    var <X : Out<Out<I>>> ok7: P
    var <X : Out<In<O>>> ok8: P
    var <X : Out<In<P>>> ok9: P
    var <X : I> Ok10: P
    var <X : In<O>> Ok11: P
    var <X : In<In<I>>> Ok12: P
    var <X : Out<I>> Ok13: P

    val <X : <!TYPE_PARAMETER_VARIANCE_CONFLICT(O, out, in, O)!>O<!>> neOk1: P
    val <X : In<<!TYPE_PARAMETER_VARIANCE_CONFLICT(I, in, out, In<I>)!>I<!>>> neOk2: P
    val <X : In<In<<!TYPE_PARAMETER_VARIANCE_CONFLICT(O, out, in, In<In<O>>)!>O<!>>>> neOk3: P
    val <X : Inv<<!TYPE_PARAMETER_VARIANCE_CONFLICT(I, in, invariant, Inv<I>)!>I<!>>> neOk4: P
    val <X : Inv<<!TYPE_PARAMETER_VARIANCE_CONFLICT(O, out, invariant, Inv<O>)!>O<!>>> neOk5: P
    var <X : In<In<<!TYPE_PARAMETER_VARIANCE_CONFLICT(O, out, in, In<In<O>>)!>O<!>>>> neOk6: P
}