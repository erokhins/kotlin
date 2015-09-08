// Auto-generated by org.jetbrains.kotlin.generators.tests.GenerateRangesCodegenTestData. DO NOT EDIT!
import java.util.ArrayList

import java.lang.Integer.MAX_VALUE as MaxI
import java.lang.Integer.MIN_VALUE as MinI
import java.lang.Byte.MAX_VALUE as MaxB
import java.lang.Byte.MIN_VALUE as MinB
import java.lang.Short.MAX_VALUE as MaxS
import java.lang.Short.MIN_VALUE as MinS
import java.lang.Long.MAX_VALUE as MaxL
import java.lang.Long.MIN_VALUE as MinL
import java.lang.Character.MAX_VALUE as MaxC
import java.lang.Character.MIN_VALUE as MinC

fun box(): String {
    val list1 = ArrayList<Int>()
    for (i in MaxI..MaxI) {
        list1.add(i)
        if (list1.size() > 23) break
    }
    if (list1 != listOf<Int>(MaxI)) {
        return "Wrong elements for MaxI..MaxI: $list1"
    }

    val list2 = ArrayList<Byte>()
    for (i in MaxB..MaxB) {
        list2.add(i)
        if (list2.size() > 23) break
    }
    if (list2 != listOf<Byte>(MaxB)) {
        return "Wrong elements for MaxB..MaxB: $list2"
    }

    val list3 = ArrayList<Short>()
    for (i in MaxS..MaxS) {
        list3.add(i)
        if (list3.size() > 23) break
    }
    if (list3 != listOf<Short>(MaxS)) {
        return "Wrong elements for MaxS..MaxS: $list3"
    }

    val list4 = ArrayList<Long>()
    for (i in MaxL..MaxL) {
        list4.add(i)
        if (list4.size() > 23) break
    }
    if (list4 != listOf<Long>(MaxL)) {
        return "Wrong elements for MaxL..MaxL: $list4"
    }

    val list5 = ArrayList<Char>()
    for (i in MaxC..MaxC) {
        list5.add(i)
        if (list5.size() > 23) break
    }
    if (list5 != listOf<Char>(MaxC)) {
        return "Wrong elements for MaxC..MaxC: $list5"
    }

    return "OK"
}
