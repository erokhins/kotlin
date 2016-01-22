//KT-571 Type inference failed
private fun double(d : Int) : Int = let(d * 2) {it / 10 + it * 2 % 10}
