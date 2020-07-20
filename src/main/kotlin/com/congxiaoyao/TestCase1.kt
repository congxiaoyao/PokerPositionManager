package com.congxiaoyao

import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

@ExperimentalTime
fun main(args: Array<String>) {
    println(bitCount(8))
}
fun bitCount(n: Int): Int {
    var n = n
    var count = 0
    while (n != 0) {
        n = n and n - 1
        count++
    }
    return count
}