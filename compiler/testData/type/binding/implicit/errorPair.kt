fun <T> getT(): T = null!!

val foo = getT<Pair<Pair<List<Int>>, String>>()
/*
psi: val foo = getT<Pair<Pair<List<Int>>, String>>()
type: Pair<[ERROR : Pair<List<Int>>], String>
    typeParameter: <out A> defined in kotlin.Pair
    typeProjection: [ERROR : Pair<List<Int>>]
    psi: val foo = getT<Pair<Pair<List<Int>>, String>>()
    type: [ERROR : Pair<List<Int>>]

    typeParameter: <out B> defined in kotlin.Pair
    typeProjection: String
    psi: val foo = getT<Pair<Pair<List<Int>>, String>>()
    type: String
*/