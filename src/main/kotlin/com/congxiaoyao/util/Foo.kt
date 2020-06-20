package com.congxiaoyao.util


fun main() {
    val list = mutableListOf(0, 1, 2, 3, 4, 5)

    for (i in 3..3) {
        println(i)
    }

    val accessor2 = WriteableListAccessor(list)
    accessor2.accessByRange(5, 3) { acc, gen, dir ->
        acc.set(3 * dir.value(), acc.get())
    }

    println()

    val accessor = ReadableListAccessor(list)
    accessor.accessByRange(0, accessor.end) { acc, gen, dir ->
        println(acc.get())
    }
}





