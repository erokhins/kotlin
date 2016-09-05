// Auto-generated by org.jetbrains.kotlin.generators.tests.GenerateRangesCodegenTestData. DO NOT EDIT!
// WITH_RUNTIME

import java.util.ArrayList

fun box(): String {
    val list1 = ArrayList<Int>()
    for (i in 5..5) {
        list1.add(i)
        if (list1.size > 23) break
    }
    if (list1 != listOf<Int>(5)) {
        return "Wrong elements for 5..5: $list1"
    }

    val list2 = ArrayList<Int>()
    for (i in 5.toByte()..5.toByte()) {
        list2.add(i)
        if (list2.size > 23) break
    }
    if (list2 != listOf<Int>(5)) {
        return "Wrong elements for 5.toByte()..5.toByte(): $list2"
    }

    val list3 = ArrayList<Int>()
    for (i in 5.toShort()..5.toShort()) {
        list3.add(i)
        if (list3.size > 23) break
    }
    if (list3 != listOf<Int>(5)) {
        return "Wrong elements for 5.toShort()..5.toShort(): $list3"
    }

    val list4 = ArrayList<Long>()
    for (i in 5.toLong()..5.toLong()) {
        list4.add(i)
        if (list4.size > 23) break
    }
    if (list4 != listOf<Long>(5.toLong())) {
        return "Wrong elements for 5.toLong()..5.toLong(): $list4"
    }

    val list5 = ArrayList<Char>()
    for (i in 'k'..'k') {
        list5.add(i)
        if (list5.size > 23) break
    }
    if (list5 != listOf<Char>('k')) {
        return "Wrong elements for 'k'..'k': $list5"
    }

    return "OK"
}


