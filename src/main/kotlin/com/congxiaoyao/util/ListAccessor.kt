package com.congxiaoyao.util

import java.lang.RuntimeException

open class ListAccessor<T, A : Kind<ListAccessor.K, T>>(
    private val entities: List<T>,
    var pointer: Int = 0) : Kind<ListAccessor.K, T> {

    object K {}
    object Pos : Direction { override fun value() = 1 }
    object Nag : Direction { override fun value() = -1 }

    val end get() = entities.lastIndex

    fun get() = get(0)
    fun get(next: Int) = entities[pointer + next]
    fun moveToNext() = ++pointer
    fun moveToLast() = --pointer
    fun canAccess() = pointer < entities.size && pointer > 0

    fun accessByRange(start: Int, end: Int,
                      action: (acc: A, gen: Int, dir: Direction) -> Unit) {
        val direction = Direction.valueOf(if (start < end) 1 else -1)
        val times = (end - start) * direction.value() + 1
        pointer = start
        repeat(times) {
            action(this as A, it, direction)
            pointer += direction.value()
        }
    }

    fun accessByRange(start: Int, len: Int, direction: Direction,
                      action: (accessor: A, gen: Int, direction: Direction) -> Unit) {
        if (len == 0) return
        val endIndex = start + len * direction.value()
        accessByRange(start, endIndex, action)
    }

    interface Direction {
        companion object {
            fun valueOf(value: Int) = if (value < 0) Nag else if (value > 0) Pos else throw RuntimeException()
        }
        fun value(): Int
    }
}

class ReadableListAccessor<T>(entities: List<T>) : ListAccessor<T, ReadableListAccessor<T>>(entities)
class WriteableListAccessor<T>(val entities: MutableList<T>) : ListAccessor<T, WriteableListAccessor<T>>(entities) {
    fun set(value: T) = set(0, value)
    fun set(next: Int, value: T) = entities.set(pointer + next, value)
}