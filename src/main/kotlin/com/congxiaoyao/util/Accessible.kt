package com.congxiaoyao.util

interface Accessible<T> {
    val startIndex: Int
    val len: Int
    val direction: Direction
    val sourceSize: Int
    operator fun get(index: Int): T

    sealed class Direction : Comparable<Int> {
        abstract operator fun unaryMinus(): Direction
        operator fun invoke(value: Int): Int = apply(value)
        operator fun invoke(): Int = apply(1)
        abstract fun apply(value: Int): Int


        object Pos : Direction() {
            override operator fun unaryMinus() = Neg
            override fun apply(value: Int) = value
            override fun compareTo(other: Int) = 1 - other
        }

        object Neg : Direction() {
            override operator fun unaryMinus() = Pos
            override fun apply(value: Int) = -value
            override fun compareTo(other: Int) = -1 - other
        }
    }

    companion object {
        fun <T> fromSource(source: ReadableSource<T>): Accessible<T> = object : FullAccessImpl<T>() {
            override val sourceSize: Int get() = source.sourceSize
            override fun read(index: Int) = source.read(index)
        }

        fun <T> fromSource(source: ReadWriteSource<T>): ReadWriteAccessible<T> =
            object : FullAccessImpl<T>(), ReadWriteSource<T> by source {
                override fun write(index: Int, value: T) {
                    source.write(index, value)
                }
            }
    }
}

interface ReadWriteAccessible<T> : Accessible<T> {
    operator fun set(index: Int, value: T)
}

interface Pointer<T> {
    val accessIndex: Int
    val sourceIndex: Int get() = sourceIndex(0)
    fun sourceIndex(offset: Int): Int
    fun get() = get(0)
    fun get(offset: Int): T
    fun canAccessFromSource() = canAccessFromSource(0)
    fun canAccessFromSource(offset: Int): Boolean
}

interface RWPointer<T> : Pointer<T> {
    fun set(offset: Int, value: T)
    fun set(value: T) = set(0, value)
}

interface PointerHandle {
    fun moveToNext()
}

interface ReadableSource<T> {
    val sourceSize: Int
    fun read(index: Int): T
}

interface ReadWriteSource<T> : ReadableSource<T> {
    fun write(index: Int, value: T)
}

private abstract class FullAccessImpl<T> : ReadWriteAccessible<T>, ReadWriteSource<T> {
    override val startIndex = 0
    override val len get() = sourceSize
    override val direction = Accessible.Direction.Pos
    override fun get(index: Int) = read(index)
    override fun set(index: Int, value: T) = write(index, value)
    override fun write(index: Int, value: T) {}
    override fun toString() = string()
}

private class PointerImpl<T>(private val accessible: Accessible<T>,
                             private var pointer: Int) : RWPointer<T>, PointerHandle {
    override val accessIndex get() = pointer
    override fun sourceIndex(offset: Int) = accessible.startIndex + accessible.direction(pointer + offset)
    override fun get(offset: Int) = accessible[pointer + offset]
    override fun canAccessFromSource(offset: Int) = sourceIndex(offset) in 0 until accessible.sourceSize
    override fun set(offset: Int, value: T) = (accessible as ReadWriteAccessible<T>).set(pointer + offset, value)
    override fun moveToNext() {
        pointer++
    }
}

private open class AccessibleIterator<T>(private val accessible: Accessible<T>) : Iterator<T> {
    var index = 0
    override fun hasNext() = index < accessible.len
    override fun next() = accessible[index++]
}

private inline fun <T> Accessible<T>.transform(accessLen: Int,
                                               direction: Accessible.Direction = this.direction,
                                               crossinline mapIndex: (index: Int) -> Int): Accessible<T> =
    object : ReadWriteAccessible<T> {
        val parent = this@transform
        override val startIndex = parent.startIndex + parent.direction(mapIndex(0))
        override val len = accessLen
        override val sourceSize = parent.sourceSize
        override val direction = direction
        override fun get(index: Int) = parent[mapIndex(index)]
        override fun set(index: Int, value: T) = (parent as ReadWriteAccessible).set(mapIndex(index), value)
        override fun toString() = string()
    }

private inline fun <T> ReadWriteAccessible<T>.implementsBy(implementation: Accessible<T>.() -> Accessible<T>)
        : ReadWriteAccessible<T> = implementation(this as Accessible<T>) as ReadWriteAccessible<T>

private fun <T> Accessible<T>.string(): String {
    val pointer = access(0)
    return buildString {
        var index = 0
        var i = -direction(startIndex)
        val firIndex = if (direction > 0) startIndex else endIndex
        val secIndex = if (direction < 0) startIndex else endIndex
        while (pointer.canAccessFromSource(i)) {
            val data = pointer.get(i).toString()
            if (index == firIndex) {
                append(when (direction) {
                    Accessible.Direction.Pos -> "["
                    Accessible.Direction.Neg -> "<"
                })
                append(data)
                if (index == secIndex) {
                    append(when (direction) {
                        Accessible.Direction.Pos -> ">"
                        Accessible.Direction.Neg -> "]"
                    })
                }
            } else if (index == secIndex) {
                append(data)
                append(when (direction) {
                    Accessible.Direction.Pos -> ">"
                    Accessible.Direction.Neg -> "]"
                })
            } else {
                append(data)
            }
            append(",")
            index++
            i += direction()
        }
        if (lastOrNull() == ',') {
            deleteCharAt(lastIndex)
        }
    }
}

fun <T> Accessible<T>.access(index: Int): Pointer<T> = PointerImpl(this, index)

fun <T> ReadWriteAccessible<T>.access(index: Int): RWPointer<T> = PointerImpl(this, index)

fun <T> Accessible<T>.first(): T {
    return get(0)
}

fun <T> Accessible<T>.firstOrNull(): T? {
    if (!isEmpty()) {
        return get(0)
    }
    return null
}

fun <T> Accessible<T>.last(): T {
    return get(endIndex)
}

fun <T> Accessible<T>.lastOrNull(): T? {
    if (!isEmpty()) {
        return get(endIndex)
    }
    return null
}

fun <T> Accessible<T>.reverse() = transform(len, -direction) { len - 1 - it }
fun <T> ReadWriteAccessible<T>.reverse() = implementsBy { reverse() }

fun <T> Accessible<T>.reverseTo(direction: Accessible.Direction) = if (this.direction == direction) this else reverse()
fun <T> ReadWriteAccessible<T>.reverseTo(direction: Accessible.Direction) = implementsBy { reverseTo(direction) }

fun <T> Accessible<T>.take(n: Int): Accessible<T> {
    require(n >= 0) {
        "Requested element count $n is less than zero."
    }
    return transform(n.coerceAtMost(len)) { it }
}

fun <T> ReadWriteAccessible<T>.take(n: Int) = implementsBy { take(n) }

fun <T> Accessible<T>.takeLast(n: Int): Accessible<T> {
    require(n >= 0) {
        "Requested element count $n is less than zero."
    }
    val newLen = n.coerceAtMost(len)
    return transform(newLen) {
        len - newLen + it
    }
}

fun <T> ReadWriteAccessible<T>.takeLast(n: Int) = implementsBy { takeLast(n) }

fun <T> Accessible<T>.drop(n: Int): Accessible<T> {
    require(n >= 0) {
        "Requested element count $n is less than zero."
    }
    val dropCount = n.coerceAtMost(len)
    return transform(len - dropCount) { it + dropCount }
}

fun <T> ReadWriteAccessible<T>.drop(n: Int) = implementsBy { drop(n) }

fun <T> Accessible<T>.dropLast(n: Int): Accessible<T> {
    require(n >= 0) {
        "Requested element count $n is less than zero."
    }
    return transform((len - n).coerceAtLeast(0)) { it }
}

fun <T> ReadWriteAccessible<T>.dropLast(n: Int) = implementsBy { dropLast(n) }

fun <T> Accessible<T>.select(index: Int): Accessible<T> {
    require(index in 0 until len) {
        "Requested index $index is out of bounds"
    }
    return transform(if (index < len) 1 else 0) { index + it }
}

fun <T> ReadWriteAccessible<T>.select(index: Int) = implementsBy { select(index) }

fun <T> Accessible<T>.select(from: Int, count: Int): Accessible<T> {
    require(from in 0 until len) {
        "Requested index $from is out of bounds"
    }
    require(count >= 0) {
        "Requested count $count is less than zero."
    }
    val newLen = count.coerceAtMost(len - from)
    return transform(newLen) { from + it }
}

fun <T> ReadWriteAccessible<T>.select(from: Int, count: Int) = implementsBy { select(from, count) }

fun <T> Accessible<T>.sourced(): Accessible<T> = object : FullAccessImpl<T>() {
    val parent = this@sourced
    override val len = parent.len
    override val sourceSize = len
    override fun read(index: Int) = parent[index]
    override fun write(index: Int, value: T) = (parent as ReadWriteAccessible).set(index, value)
}
fun <T> ReadWriteAccessible<T>.sourced() = implementsBy { sourced() }

fun <T> Accessible<T>.merge(accessible: Accessible<T>): Accessible<T> = object : FullAccessImpl<T>() {
    val parent = this@merge
    override val len = parent.len + accessible.len
    override val sourceSize = len
    override fun read(index: Int) = if (index < len) parent[index] else accessible[index - parent.len]
    override fun write(index: Int, value: T) {
        val rwParent = (parent as ReadWriteAccessible)
        val rwAccessible = accessible as ReadWriteAccessible
        if (index < len) {
            rwParent.set(index, value)
        } else {
            rwAccessible.set(index - rwParent.len, value)
        }
    }
}
fun <T> ReadWriteAccessible<T>.merge(accessible: ReadWriteAccessible<T>): ReadWriteAccessible<T> =
    implementsBy {
        merge(accessible)
    }

@JvmOverloads
fun <T> Accessible<T>.extend(n: Int, ignoreEmpty: Boolean = true): Accessible<T> {
    require(n >= 0) {
        "Requested count $n is less than zero."
    }
    if (ignoreEmpty && isEmpty()) return this

    val newLen = (n + len).let {
        if (direction > 0) {
            it.coerceAtMost(sourceSize - startIndex)
        } else {
            it.coerceAtMost(startIndex + 1)
        }
    }
    return transform(newLen) { it }
}

fun <T> ReadWriteAccessible<T>.extend(n: Int, ignoreEmpty: Boolean = true): ReadWriteAccessible<T> =
    implementsBy {
        extend(n, ignoreEmpty)
    }

@JvmOverloads
fun <T> Accessible<T>.extendBackward(n: Int, ignoreEmpty: Boolean = true): Accessible<T> {
    require(n >= 0) {
        "Requested count $n is less than zero."
    }
    if (ignoreEmpty && isEmpty()) return this
    val extend = n.let {
        if (direction > 0) {
            it.coerceAtMost(startIndex)
        } else {
            it.coerceAtMost(sourceSize - startIndex - 1)
        }
    }
    return transform(len + extend) { it - extend }
}
fun <T> ReadWriteAccessible<T>.extendBackward(n: Int, ignoreEmpty: Boolean = true): ReadWriteAccessible<T> =
    implementsBy {
        extendBackward(n, ignoreEmpty)
    }

fun <T> Accessible<T>.takeOrExtend(n: Int, ignoreEmpty: Boolean = true): Accessible<T> {
    require(n >= 0) {
        "Requested count $n is less than zero."
    }
    return if (n <= len) take(n) else extend(n - len, ignoreEmpty)
}
fun <T> ReadWriteAccessible<T>.takeOrExtend(n: Int, ignoreEmpty: Boolean = true): ReadWriteAccessible<T> =
    implementsBy {
        takeOrExtend(n, ignoreEmpty)
    }

fun <T> Accessible<T>.takeLastOrExtent(n: Int, ignoreEmpty: Boolean = true): Accessible<T> {
    require(n >= 0) {
        "Requested count $n is less than zero."
    }
    return if (n <= len) takeLast(n) else extendBackward(n - len, ignoreEmpty)
}
fun <T> ReadWriteAccessible<T>.takeLastOrExtent(n: Int, ignoreEmpty: Boolean = true): ReadWriteAccessible<T> =
    implementsBy {
        takeLastOrExtent(n, ignoreEmpty)
    }

val <T> Accessible<T>.endIndex get() = startIndex + direction(len - 1)

fun <T> Accessible<T>.isEmpty() = len == 0

fun <T> Accessible<T>.asIterable(): Iterable<T> = Iterable { AccessibleIterator(this) }

fun <T> Accessible<T>.asSequence(): Sequence<T> = Sequence { AccessibleIterator(this) }

inline fun <T> Accessible<T>.forEach(action: (T) -> Unit) = repeat(len) { action(get(it)) }
inline fun <T> Accessible<T>.forEachIndexed(action: (index: Int, value: T) -> Unit) =
    repeat(len) { action(it, get(it)) }

inline fun <T> Accessible<T>.forEachByPointer(action: (pointer: Pointer<T>) -> Unit) {
    if (isEmpty()) return
    val pointer = access(0)
    if (pointer is PointerHandle) {
        repeat(len) {
            action(pointer)
            pointer.moveToNext()
        }
    } else {
        action(pointer)
        for (i in 1 until len) action(access(i))
    }
}

inline fun <T> ReadWriteAccessible<T>.forEachByRwPointer(action: (pointer: RWPointer<T>) -> Unit) {
    (this as Accessible<T>).forEachByPointer {
        action(it as RWPointer<T>)
    }
}

@JvmName("asReadOnlyAccessible")
fun <T> List<T>.asAccessible(): Accessible<T> = object : FullAccessImpl<T>() {
    val list = this@asAccessible
    override val sourceSize = list.size
    override fun read(index: Int) = list[index]
}

fun <T> Array<T>.asAccessible(): ReadWriteAccessible<T> = object : FullAccessImpl<T>() {
    val array = this@asAccessible
    override val sourceSize = array.size
    override fun read(index: Int) = array[index]
    override fun write(index: Int, value: T) {
        array[index] = value
    }
}

@JvmName("asReadWriteAccessible")
fun <T> MutableList<T>.asAccessible(): ReadWriteAccessible<T> = object : FullAccessImpl<T>() {
    val list = this@asAccessible
    override val sourceSize get() = list.size
    override fun read(index: Int) = list[index]
    override fun write(index: Int, value: T) {
        list[index] = value
    }
}