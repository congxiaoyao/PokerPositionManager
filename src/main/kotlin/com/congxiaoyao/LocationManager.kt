package com.congxiaoyao

import com.congxiaoyao.util.*
import java.util.*

class LocationManager<T>(
    private val interval: Int,
    private val elementWidth: Int,
    private val extraMargin: Int = 0) {

    private val entities = mutableListOf<Entity<T>>()
    private val locProxy = object : EntityPropertyAccessor<Int> {
        override fun get(index: Int) = entities[index].location
        override fun set(index: Int, value: Int) {
            entities[index].location = value
        }
    }
    private val tagsProxy = object : EntityPropertyAccessor<T> {
        override fun get(index: Int) = entities[index].tag
        override fun set(index: Int, value: T) {
            entities[index].tag = value
        }
    }

    val width get() = if (entities.isEmpty()) 0 else (interval * entities.size - 1) + elementWidth
    val sequence get() = entities.asSequence()
    private val accessible get() = entities.asAccessible()

    /**
     * 将[selected]中的所有[Entity]移动[dis]距离，此方法计算经过移动后所有[Entity]的新位置
     * 主要处理流程分如下步骤：
     * 1.异常检查
     * 2.对于非连续，未移动的[Entity]，做聚合，使之成为位置连续的[Entity]
     * 3.确保非选中[Entity]与选中[Entity]之间保持额外的[extraMargin]的距离
     * 4.移动[dis]距离，根据移动后的位置对[Entity]重新排序
     *
     * @param selected 要移动的Entity
     * @param anchor 发生聚合时的聚合中心Entity
     * @param dis 位移值
     */
    fun moveSelectedEntitiesOrg(selected: List<Entity<T>>, anchor: Entity<T>, dis: Int) {
        if (selected.isEmpty()) return
        //sort by location
        val target = (selected as? MutableList ?: selected.toMutableList()).apply { sortBy { it.location } }
        //check selected entities's location legal
        target.asSequence().map { it.location }.forEachWindowed { pre, cur ->
            if (pre == cur) throw RuntimeException("location重复")
        }
        // ensure anchor in selected
        if (!selected.any { it.location == anchor.location }) {
            throw RuntimeException("anchor 不在 selected 中")
        }
        val isTargetConsecutive = isConsecutiveArranged(target)
        val anchorIndex = entities.indexOfFirst { anchor.tag == it.tag }
        val isTargetMoved = anchorIndex * interval != anchor.location

        //something went wrong when selected entities moved and not consecutive
        if (isTargetMoved && !isTargetConsecutive) {
            throw RuntimeException("internal error")
        }

        var bitset = markSelectedIndexes(target)

        //aggregate entities
        if (!isTargetMoved && !isTargetConsecutive) {
            // entities on the right side of anchor
            var offset = 0
            accessible.drop(anchorIndex + 1).reverse().forEachByRwPointer {
                if (bitset[it.sourceIndex]) {
                    offset++
                } else {
                    it.set(offset, it.get())
                }
            }
            //entities on the left side of anchor
            offset = 0
            accessible.take(anchorIndex).forEachByRwPointer {
                if (bitset[it.sourceIndex]) {
                    offset--
                } else {
                    it.set(offset, it.get())
                }
            }
            //selected entities
            accessible.drop(anchorIndex + offset).take(target.size).forEachByRwPointer {
                it.set(target[it.accessIndex])
            }

            entities.forEachIndexed { index, entity -> entity.location = index * interval }

            bitset = markSelectedIndexes(target)
        }

        val minSelectedIndex = bitset.nextSetBit(0)
        val maxSelectedIndex = minSelectedIndex + selected.size - 1

        //ensure extra margin
        accessible.take(minSelectedIndex).forEachByRwPointer {
            it.get().location = it.sourceIndex * interval - extraMargin
        }
        accessible.drop(maxSelectedIndex + 1).forEachByRwPointer {
            it.get().location = it.sourceIndex * interval + extraMargin
        }

        //move entities
        target.forEach { it.location += dis }

        //calculate the range of entities on the left of the selected entities that need to be moved to the right
        val minSelectedLoc = locProxy[minSelectedIndex]
        var startIndex = minSelectedIndex
        while (startIndex > 0 && locProxy[startIndex - 1] >= minSelectedLoc) startIndex--
        var endIndex = minSelectedIndex - 1

        //no entities need to be moved to the right
        if (startIndex > endIndex) {
            //calculate the range of entities on the right of the selected entities that need to be moved to the left
            val maxSelectedLoc = locProxy[maxSelectedIndex]
            endIndex = maxSelectedIndex
            while (endIndex < entities.lastIndex && locProxy[endIndex + 1] <= maxSelectedLoc) endIndex++
            startIndex = maxSelectedIndex + 1
        }

        //sort the entities that need to be moved
        if (startIndex <= endIndex) {
            val goRight = startIndex < anchorIndex
            val indexes = if (goRight) endIndex downTo startIndex else startIndex..endIndex
            for (i in indexes) {
                val newIndex = if (goRight) i + target.size else i - target.size
                entities[newIndex] = entities[i]
                locProxy[newIndex] += (target.size * interval + 2 * extraMargin) * if (goRight) 1 else -1
            }
            repeat(target.size) {
                val newIndex = if (goRight) startIndex + it else endIndex - target.size + it + 1
                entities[newIndex] = target[it]
            }
        }
    }

    fun moveSelectedEntities(selected: List<Entity<T>>, anchor: Entity<T>, dis: Int) {
        if (selected.isEmpty()) return
        //sort by location
        val target = (selected as? MutableList ?: selected.toMutableList()).apply { sortBy { it.location } }
        //check selected entities's location legal
        target.asSequence().map { it.location }.forEachWindowed { pre, cur ->
            if (pre == cur) throw RuntimeException("location重复")
        }
        // ensure anchor in selected
        if (!selected.any { it.location == anchor.location }) {
            throw RuntimeException("anchor 不在 selected 中")
        }
        val isTargetConsecutive = isConsecutiveArranged(target)
        val anchorIndex = entities.indexOfFirst { anchor.tag == it.tag }
        val isTargetMoved = anchorIndex * interval != anchor.location

        //something went wrong when selected entities moved and not consecutive
        if (isTargetMoved && !isTargetConsecutive) {
            throw RuntimeException("internal error")
        }

        var bitset = markSelectedIndexes(target)

        //aggregate entities
        if (!isTargetMoved && !isTargetConsecutive) {
            // entities on the right side of anchor
            var offset = 0
            accessible.drop(anchorIndex + 1).reverse().forEachByRwPointer {
                if (bitset[it.sourceIndex]) {
                    offset--
                } else {
                    it.set(offset, it.get())
                }
            }
            //entities on the left side of anchor
            offset = 0
            accessible.take(anchorIndex).forEachByRwPointer {
                if (bitset[it.sourceIndex]) {
                    offset--
                } else {
                    it.set(offset, it.get())
                }
            }
            //selected entities
            accessible.drop(anchorIndex + offset).take(target.size).forEachByRwPointer {
                it.set(target[it.accessIndex])
            }

            entities.forEachIndexed { index, entity -> entity.location = index * interval }

            bitset = markSelectedIndexes(target)
        }

        val minSelectedIndex = bitset.nextSetBit(0)
        val maxSelectedIndex = minSelectedIndex + selected.size - 1

        //ensure extra margin
        accessible.take(minSelectedIndex).forEachByRwPointer {
            it.get().location = it.sourceIndex * interval - extraMargin
        }
        accessible.drop(maxSelectedIndex + 1).forEachByRwPointer {
            it.get().location = it.sourceIndex * interval + extraMargin
        }

        //move entities
        target.forEach { it.location += dis }

        val minSelectedLoc = locProxy[minSelectedIndex]
        val maxSelectedLoc = locProxy[maxSelectedIndex]
        val movedEntities = if (minSelectedIndex > 0 && minSelectedLoc <= locProxy[minSelectedIndex - 1]) {
            accessible.select(minSelectedIndex - 1).reverse().extend((locProxy[minSelectedIndex - 1] - minSelectedLoc) / interval)
        } else if (maxSelectedIndex < entities.lastIndex && maxSelectedLoc >= locProxy[maxSelectedIndex + 1]) {
            accessible.drop(maxSelectedIndex + 1)
                .take(1 + (maxSelectedLoc - locProxy[maxSelectedIndex + 1]) / interval)
        } else null
        movedEntities?.forEachByRwPointer {
            val dir = movedEntities.direction
            val offset = -target.size
            it.set(offset, it.get())
            it.get().location = it.sourceIndex(offset) * interval - dir(extraMargin)
        }
        val targetAcc = movedEntities?.let {
            target.asAccessible().reverseTo(-movedEntities.direction)
        }
        movedEntities?.reverse()?.takeOrExtend(target.size)?.forEachByRwPointer {
            it.set(targetAcc!![it.accessIndex])
        }
//        println(this)
    }

    private fun markSelectedIndexes(selected: List<Entity<T>>): BitSet {
        val bitset = BitSet()
        var pa = 0
        var ps = 0
        while (pa < entities.size && ps < selected.size) {
            if (tagsProxy[pa] == selected[ps].tag) {
                bitset[pa] = true
                ps++
            }
            pa++
        }
        return bitset
    }

    fun addElement(tag: T): Boolean {
        if (!isStandardArranged()) {
            throw RuntimeException("需要标准排布")
        }

        if (sequence.any { it.tag == tag }) {
            throw RuntimeException("重复添加")
        }

        val location = if (entities.isEmpty()) {
            0
        } else {
            entities.last().location + interval
        }
        return entities.add(Entity(location, tag))
    }

    fun removeByTag(tag: T): Boolean {
        val index = sequence.indexOfFirst { it.tag == tag }
        if (index < 0) return false
        entities.removeAt(index)
        for (i in index until entities.size) {
            locProxy[i] -= interval
        }
        return true
    }

    fun clear() {
        entities.clear()
    }

    fun findByTag(tag: T) = sequence.find { it.tag == tag }

    private fun isStandardArranged(entities: List<Entity<T>> = this.entities, startLocation: Int = 0): Boolean {
        if (entities.isEmpty()) {
            return true
        }

        entities.forEachIndexed { index, entity ->
            if (entity.location != startLocation + index * interval) {
                return false
            }
        }
        return true
    }

    /**
     * @return 是否是连续排布
     */
    private fun isConsecutiveArranged(entities: List<Entity<T>>): Boolean {
        entities.asSequence().map { it.location }.forEachWindowed { pre, cur ->
            if (cur - pre != interval) {
                return false
            }
        }
        return true
    }

    private inline fun <T> Sequence<T>.forEachWindowed(action: (pre: T, cur: T) -> Unit) {
        val iterator = iterator()
        //empty
        if (!iterator.hasNext()) {
            return
        }
        var pre = iterator.next()
        while (iterator.hasNext()) {
            val cur = iterator.next()
            action(pre, cur)
            pre = cur
        }
    }

    override fun toString(): String {
        return buildString {
            append("(")
            if (entities.isNotEmpty()) {
                append(entities.first().toString())
                if (entities.size > 1) {
                    entities.drop(1).forEach {
                        append(",")
                        append(it.toString())
                    }
                }
            }
            append(")")
        }
    }

    class Entity<T>(var location: Int, var tag: T) {
        override fun toString(): String {
            return "$tag($location)"
        }
    }


    private interface EntityPropertyAccessor<T> {
        operator fun get(index: Int): T
        operator fun set(index: Int, value: T)
    }
}