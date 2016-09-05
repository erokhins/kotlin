// WITH_RUNTIME

import java.util.ArrayList

fun dense(x: Int): Int {
    return when (x) {
        -4 -> 9
        -1 -> 10
        0 -> 11
        1 -> 12
        4 -> 13
        5 -> 14
        6 -> 15
        7 -> 16
        8 -> 17
        9 -> 18
        else -> 19
    }
}

fun box(): String {
    var result = (-5..10).map(::dense).joinToString()

    if (result != "19, 9, 19, 19, 10, 11, 12, 19, 19, 13, 14, 15, 16, 17, 18, 19") return "dense:" + result
    return "OK"
}


