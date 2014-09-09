package k

trait In<in T>
trait Out<out T>
trait Inv<T>

trait Multi<in I, out O, P>

trait Test<in I, in I1, out O, out O1, P, P1> {
    fun severalParams(a: <!TYPE_PARAMETER_VARIANCE_CONFLICT(O, out, in, O)!>O<!>, b: <!TYPE_PARAMETER_VARIANCE_CONFLICT(O1, out, in, O1)!>O1<!>, c: In<<!TYPE_PARAMETER_VARIANCE_CONFLICT(I, in, out, k.In<I>)!>I<!>>, d: Multi<<!TYPE_PARAMETER_VARIANCE_CONFLICT(I, in, out, IGNORE)!>I<!>, <!TYPE_PARAMETER_VARIANCE_CONFLICT(O, out, in, IGNORE)!>O<!>, <!TYPE_PARAMETER_VARIANCE_CONFLICT(I1, in, invariant, IGNORE)!>I1<!>>): Multi<<!TYPE_PARAMETER_VARIANCE_CONFLICT(O, out, in, IGNORE)!>O<!>, <!TYPE_PARAMETER_VARIANCE_CONFLICT(I, in, out, IGNORE)!>I<!>, <!TYPE_PARAMETER_VARIANCE_CONFLICT(O1, out, invariant, IGNORE)!>O1<!>>

    val complexType: Multi<In<<!TYPE_PARAMETER_VARIANCE_CONFLICT(I, in, out, IGNORE)!>I<!>>, Out<Multi<<!TYPE_PARAMETER_VARIANCE_CONFLICT(O, out, in, IGNORE)!>O<!>, <!TYPE_PARAMETER_VARIANCE_CONFLICT(I, in, out, IGNORE)!>I<!>, <!TYPE_PARAMETER_VARIANCE_CONFLICT(I1, in, invariant, IGNORE)!>I1<!>>>, <!TYPE_PARAMETER_VARIANCE_CONFLICT(O1, out, invariant, IGNORE)!>O1<!>>

    val conflictProjection: Multi<<!TYPE_PARAMETER_VARIANCE_CONFLICT(O, out, in, IGNORE)!>O<!>, Multi<<!TYPE_PARAMETER_VARIANCE_CONFLICT(O, out, in, IGNORE)!>O<!>, <!CONFLICTING_PROJECTION!>in<!> In<O>, In<<!TYPE_PARAMETER_VARIANCE_CONFLICT(I1, in, invariant, IGNORE)!>I1<!>>>, <!TYPE_PARAMETER_VARIANCE_CONFLICT(O1, out, invariant, IGNORE)!>O1<!>>
}