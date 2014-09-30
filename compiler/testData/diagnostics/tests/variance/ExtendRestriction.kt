trait In<in T>
trait Out<out T>
trait Inv<T>


class T_1<in I, out O, P> : In<I>, Inv<<!TYPE_PARAMETER_VARIANCE_CONFLICT(I, in, invariant, Inv<I>)!>I<!>>, Out<O>

trait T_ok2<in I, out O, P> : In<I>
trait T_ok3<in I, out O, P> : In<In<O>>
trait T_ok4<in I, out O, P> : Inv<P>
trait T_ok6<in I, out O, P> : Out<O>
trait T_ok8<in I, out O, P> : Out<In<P>>

trait T_neOk2<in I, out O, P> : In<<!TYPE_PARAMETER_VARIANCE_CONFLICT(O, out, in, In<O>)!>O<!>>
trait T_neOk3<in I, out O, P> : In<In<<!TYPE_PARAMETER_VARIANCE_CONFLICT(I, in, out, In<In<I>>)!>I<!>>>
trait T_neOk4<in I, out O, P> : Inv<<!TYPE_PARAMETER_VARIANCE_CONFLICT(I, in, invariant, Inv<I>)!>I<!>>
trait T_neOk5<in I, out O, P> : Inv<<!TYPE_PARAMETER_VARIANCE_CONFLICT(O, out, invariant, Inv<O>)!>O<!>>

