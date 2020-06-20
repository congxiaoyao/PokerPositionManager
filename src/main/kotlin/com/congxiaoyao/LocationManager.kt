package com.congxiaoyao

import java.util.*

class LocationManager<T>(
    private val interval: Int,
    private val elementWidth: Int,
    private val extraMargin: Int = 0) {

    private val entities = mutableListOf<Entity<T>>()
//    private val entityAccessor = EntityAccessor<T>(entities)
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
    val sequence: Sequence<Entity<T>> get() = entities.asSequence()

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
            for (i in entities.lastIndex downTo anchorIndex + 1) {
                if (bitset[i]) {
                    offset++
                } else {
                    val newIndex = i + offset
                    entities[newIndex] = entities[i]
                    locProxy[newIndex] = newIndex * interval
                }
            }
            //entities on the left side of anchor
            offset = 0
            for (i in 0..anchorIndex) {
                if (bitset[i]) {
                    offset++
                } else {
                    val newIndex = i - offset
                    entities[newIndex] = entities[i]
                    locProxy[newIndex] = newIndex * interval
                }
            }
            //selected entities
            target.forEachIndexed { index, entity ->
                val newIndex = index + anchorIndex - offset + 1
                entities[newIndex] = entity
                entity.location = newIndex * interval
            }
            bitset = markSelectedIndexes(target)
        }

        val minSelectedIndex = bitset.nextSetBit(0)
        var maxSelectedIndex = minSelectedIndex + selected.size - 1

        //ensure extra margin
        for (i in 0 until minSelectedIndex) {
            val entity = entities[i]
            entity.location = i * interval - extraMargin
        }
        for (i in maxSelectedIndex + 1 until entities.size) {
            val entity = entities[i]
            entity.location = i * interval + extraMargin
        }

        //move entities TODO(try do it by locProxy)
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

        println(markSelectedIndexes(target))
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

    class Entity<T>(var location: Int, var tag: T)

    private interface EntityPropertyAccessor<T> {
        operator fun get(index: Int): T
        operator fun set(index: Int, value: T)
    }
}