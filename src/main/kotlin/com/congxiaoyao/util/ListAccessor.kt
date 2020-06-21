package com.congxiaoyao.util

open class ListAccessor<T, A : Kind<ListAccessor.K, T>>(
    private val entities: List<T>,
    var pointer: Int = 0) : Kind<ListAccessor.K, T> {

    object K {}

    val end get() = entities.lastIndex

    fun get() = get(0)
    fun get(next: Int) = entities[pointer + next]
    fun moveToNext() = ++pointer
    fun moveToLast() = --pointer
    fun canAccess() = pointer < entities.size && pointer > 0

    inline fun accessByRange(start: Int, end: Int, action: AccessCallback<A>) {
        val direction = Direction.valueOf(end - start).value()
        val times = (end - start) * direction + 1
        var index = start
        repeat(times) {
            pointer = index
            action(unwrap(), it, direction)
            index += direction
        }
    }

    inline fun accessByRange(start: Int, len: Int, direction: Direction, action: AccessCallback<A>) {
        if (len == 0) return
        val endIndex = start + len * direction.value()
        accessByRange(start, endIndex, action)
    }

    fun unwrap() = this as A

    sealed class Direction {
        companion object {
            fun valueOf(value: Int) = if (value < 0) Nag else Pos
        }
        abstract fun value(): Int

        object Pos : Direction() {
            override fun value() = 1
        }
        object Nag : Direction() {
            override fun value() = -1
        }
    }
}
private typealias AccessCallback<A> = ((aac: A, gen: Int, dir: Int) -> Unit)

class ReadableListAccessor<T>(entities: List<T>) : ListAccessor<T, ReadableListAccessor<T>>(entities)
class WriteableListAccessor<T>(val entities: MutableList<T>) : ListAccessor<T, WriteableListAccessor<T>>(entities) {
    fun set(value: T) = set(0, value)
    fun set(next: Int, value: T) = entities.set(pointer + next, value)
}