trait In<in T>
trait Out<out T>
trait Inv<T>

fun <T> getT(): T = null!!

class Test<in I, out O, P>(
        val type1: <!TYPE_VARIANCE_CONFLICT(I; in; out; I)!>I<!>,
        val type2: O,
        val type3: P,
        val type4: In<I>,
        val type5: In<<!TYPE_VARIANCE_CONFLICT(O; out; in; In<O>)!>O<!>>,

        var type6: <!TYPE_VARIANCE_CONFLICT(I; in; invariant; I)!>I<!>,
        var type7: <!TYPE_VARIANCE_CONFLICT(O; out; invariant; O)!>O<!>,
        var type8: P,
        var type9: In<<!TYPE_VARIANCE_CONFLICT(I; in; invariant; In<I>)!>I<!>>,
        var type0: In<<!TYPE_VARIANCE_CONFLICT(O; out; invariant; In<O>)!>O<!>>,

        type11: I,
        type12: O,
        type13: P,
        type14: In<I>,
        type15: In<O>
)