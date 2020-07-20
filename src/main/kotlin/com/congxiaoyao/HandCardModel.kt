package com.congxiaoyao

import com.congxiaoyao.util.*

class HandCardModel<T>(
    private val interval: Int,
    private val elementWidth: Int,
    private val extraMargin: Int = 0) {

    private var state: State = State.Normal
    private val entityGroup = EntityGroup()

    val entities = entityGroup.entities
    val width get() = if (entities.isEmpty()) 0 else (interval * entities.len - 1) + elementWidth

    val isSorting get() = state is State.Sorting
    val isAggregated get() = state is State.Aggregated
    val isNormalState get() = state is State.Normal

    fun aggregate(anchor: T) = aggregate(findByTag(anchor)!!)
    fun aggregate(anchorIndex: Int) = aggregate(entities[anchorIndex])
    fun aggregate(anchor: Entity<T>){
        if (state >= State.Aggregated) return
        check(anchor.isSelected) {
            "anchor not Selected"
        }
        val anchorIndex = entityGroup.indexOf(anchor)
        require(anchorIndex >= 0){
            "anchor not in entities"
        }

        val selected = entityGroup.requireList()
        val unSelected = entityGroup.requireList()
        var leftSize = 0
        entities.forEachIndexed { index, entity ->
            if (entity.isSelected) {
                selected += entity
            } else {
                unSelected += entity
                if (index < anchorIndex) leftSize++
            }
        }

        entityGroup.resetSource(selected, unSelected, leftSize)

        entities.forEachIndexed { index, entity -> entity moveTo index * interval }

        state = State.Aggregated
    }

    /**
     * 在[State.Aggregated]的基础之上，将选中的牌移动[dis]距离，在移动之前，会确保左右两侧的未选中牌与选中的牌额外间距[extraMargin]
     * 在此次移动过程中，会检查是否因为移动而覆盖了左右两侧的未选中牌，如果某一侧的牌被选中牌覆盖，则被覆盖牌将移动至选中牌的另外一侧
     *
     * @param dis 选中牌移动的距离
     * @throws IllegalStateException [State.Normal]状态下调用此方法将抛出异常
     */
    fun moveAndSort(dis: Int) {
        check(state > State.Normal) {
            "can not move entities on current state"
        }
        require(!entityGroup.selected.isEmpty()) {
            "no entity selected"
        }

        val selected = entityGroup.selected
        val leftEntities = entityGroup.leftUnSelected
        val rightEntities = entityGroup.rightUnSelected

        //将左侧 右侧未选中的牌位移，使之距离选中牌extraMargin
        if (state == State.Aggregated) {
            leftEntities move -extraMargin
            rightEntities move extraMargin
        }

        //将选中牌移动[dis]距离
        selected move dis

        //选中的牌最左侧的location
        val minSelLoc = selected.first().location
        //选中的牌最右侧的location
        val maxSelLoc = selected.last().location
        //左侧未选中最右侧location
        val leftMaxLoc = leftEntities.lastOrNull()?.location ?: (minSelLoc - 1)
        //右侧未选中最左侧location
        val rightMinLoc = rightEntities.firstOrNull()?.location ?: (maxSelLoc + 1)

        //因为移动而被覆盖的牌
        val covered = when {
            minSelLoc <= leftMaxLoc -> leftEntities
                .takeLast((leftMaxLoc - minSelLoc) / interval + 1)
            maxSelLoc >= rightMinLoc -> rightEntities
                .take((maxSelLoc - rightMinLoc) / interval + 1).reverse()
            else -> null
        }

        //将被覆盖的牌移动至选中牌的另一侧
        covered?.apply {
            this move direction(selected.len * interval + 2 * extraMargin)
            entityGroup.offsetSplitPoint(-direction(len))
        }

        state = State.Sorting
    }

    fun cancelSorting() {
        check(state is State.Sorting) {
            "当前未处于sorting状态"
        }
        check(entityGroup.backUp != null) {
            "数据恢复异常"
        }
        entityGroup.restoreFromBackup()
        normalizeInternal()
        state = State.Normal
    }

    fun indexOf(tag: T) = entityGroup.indexOf(tag)

    fun isSelect(index: Int) = isSelect(entities[index])
    fun isSelect(tag: T) = isSelect(findByTag(tag)!!)
    fun isSelect(entity: Entity<T>) = entity.isSelected

    fun select(tag: T) = select(findByTag(tag)!!)
    fun select(index: Int) = select(entities[index])
    fun select(entity: Entity<T>) = markSelectState(entity, true)

    fun unSelect(tag: T) = unSelect(findByTag(tag)!!)
    fun unSelect(index: Int) = unSelect(entities[index])
    fun unSelect(entity: Entity<T>) = markSelectState(entity, false)

    fun toggleSelect(tag: T) = toggleSelect(findByTag(tag)!!)
    fun toggleSelect(index: Int) = toggleSelect(entities[index])
    fun toggleSelect(entity: Entity<T>) = markSelectState(entity, !isSelect(entity))

    fun unSelectAll() {
        check(state < State.Sorting)
        entities.forEach { it.isSelected = false }
        state = State.Normal
    }

    private fun markSelectState(entity: Entity<T>, isSelect: Boolean) {
        check(state < State.Sorting) {
            "sorting状态不允许选择"
        }
        if (entity.isSelected != isSelect) {
            entity.isSelected = isSelect
            state = State.Normal
        }
    }

    fun normalize() {
        if (state !is State.Sorting) return
        normalizeInternal()
        state = State.Aggregated
    }

    private fun normalizeInternal() = entities.forEachIndexed { index, entity ->
        entity moveTo index * interval
    }

    fun currentState() = state

    fun findByTag(tag: T) = entityGroup.find { it.tag == tag }

    fun addElement(tag: T, index: Int = entities.len) {
        require(findByTag(tag) == null) {
            "重复添加"
        }
        require(index in 0..entities.len) {
            "index out of bounds"
        }
        check(state < State.Sorting) {
            "禁止在Sorting状态添加"
        }
        entityGroup.addEntity(Entity(0, tag), index)
        normalizeInternal()
        state = State.Normal
    }

    fun addAllElement(tags: List<T>, index: Int) {
        require(tags.all { findByTag(it) == null }) {
            "重复添加"
        }
        require(index in 0..entities.len) {
            "index out of bounds"
        }
        check(state < State.Sorting) {
            "禁止在Sorting状态添加"
        }
        entityGroup.addAll( tags.map { Entity(0, it) }, index)
        normalizeInternal()
        state = State.Normal
    }

    fun removeSelected() {
        if (state >= State.Aggregated) {
            entityGroup.removeSelectedEntities()
        } else {
            val newList = entityGroup.requireList()
            entities.asSequence().filter { !it.isSelected }.toCollection(newList)
            entityGroup.resetSource(newList, entityGroup.requireList(), 0)
        }
        normalizeInternal()
        state = State.Normal
    }

    fun clear() {
        entityGroup.clear()
        state = State.Normal
    }

    private infix fun <T> Entity<T>.move(offset: Int) {
        this.location += offset
    }

    private infix fun <T> Entity<T>.moveTo(location: Int) {
        this.location = location
    }

    private infix fun <T> Accessible<Entity<T>>.move(dis: Int) {
        forEach { it.location += dis }
    }

    sealed class State : Comparable<State> {
        object Normal : State() {
            override fun compareTo(other: State) = if (other == Normal) 0 else -1
        }

        object Aggregated : State() {
            override fun compareTo(other: State) = when (other) {
                is Normal -> 1
                is Aggregated -> 0
                is Sorting -> -1
            }
        }

        object Sorting : State() {
            override fun compareTo(other: State) = if (other == Sorting) 0 else 1
        }
    }

    data class Entity<T>(var location: Int,
                         var tag: T) {
        var isSelected = false
        override fun toString(): String {
            return "$tag($location)"
        }
    }

    private inner class EntityGroup {
        private val pool = MutableList<MutableList<Entity<T>>>(6) { mutableListOf() }

        private var selectedSource = requireList()
        private var unSelectedSource = requireList()
        var splitPoint = 0

        var backUp: BackUp? = null

        val leftUnSelected = Accessible.fromSource(object : ReadableSource<Entity<T>> {
            override val sourceSize get() = splitPoint
            override fun read(index: Int) = unSelectedSource[index]
        })
        val rightUnSelected = Accessible.fromSource(object : ReadableSource<Entity<T>> {
            override val sourceSize: Int get() = unSelectedSource.size - splitPoint
            override fun read(index: Int) = unSelectedSource[index + splitPoint]
        })
        val selected = Accessible.fromSource(object : ReadableSource<Entity<T>> {
            override val sourceSize: Int get() = selectedSource.size
            override fun read(index: Int) = selectedSource[index]
        })
        val entities = Accessible.fromSource(object : ReadableSource<Entity<T>> {
            override val sourceSize: Int get() = selectedSource.size + unSelectedSource.size
            override fun read(index: Int): Entity<T> {
                require(index in 0..sourceSize)
                mapIndexToSource(index) { source, sIndex ->
                    return source[sIndex]
                }
            }
        })

        fun addEntity(entity: Entity<T>, index: Int = entities.len) {
            require(index in 0..entities.len)
            mapIndexToSource(index) { source, sIndex ->
                source.add(index, entity)
            }
        }

        fun addAll(entities: List<Entity<T>>, index: Int = this.entities.len) {
            require(index in 0..this.entities.len)
            mapIndexToSource(index) { source, sIndex ->
                source.addAll(sIndex, entities)
            }
        }

        fun removeSelectedEntities() {
            selectedSource.clear()
        }

        fun remove(index: Int) {
            require(index in 0 until entities.len)
            mapIndexToSource(index) { source, sIndex ->
                source.removeAt(sIndex)
            }
        }

        fun restoreFromBackup() {
            backUp?.apply {
                recycle(unSelectedSource)
                recycle(selectedSource)
                selectedSource = selected
                unSelectedSource = unSelected
                this@EntityGroup.splitPoint = this.splitPoint
            }
            backUp = null
        }

        fun resetSource(selected: MutableList<Entity<T>>,
                        unSelected: MutableList<Entity<T>>,
                        splitPoint: Int) {
            backUp?.apply {
                recycle(selected)
                recycle(unSelected)
            }
            backUp = BackUp(selectedSource, unSelectedSource, this.splitPoint)
            this.selectedSource = selected
            this.unSelectedSource = unSelected
            this.splitPoint = splitPoint
        }

        private inline fun <R> mapIndexToSource(index: Int, action: (source: MutableList<Entity<T>>, sIndex: Int) -> R): R {
            require(index in 0..(selectedSource.size + unSelectedSource.size))
            return when {
                index < splitPoint -> {
                    action(unSelectedSource, index)
                }
                index < splitPoint + selectedSource.size -> {
                    action(selectedSource, index - splitPoint)
                }
                else -> action(unSelectedSource, index - selectedSource.size)
            }
        }

        fun offsetSplitPoint(offset: Int) {
            splitPoint = (splitPoint + offset).coerceIn(0, unSelectedSource.size)
        }

        inline fun find(action: (Entity<T>) -> Boolean): Entity<T>? {
            return selectedSource.find(action) ?: unSelectedSource.find(action)
        }

        private inline fun indexOf(predicate: (Entity<T>) -> Boolean): Int {
            repeat(splitPoint) {
                if (predicate(unSelectedSource[it])) {
                    return it
                }
            }
            for ((index, entity) in selectedSource.withIndex()) {
                if (predicate(entity)) {
                    return index + splitPoint
                }
            }
            for (i in splitPoint until unSelectedSource.size) {
                if (predicate(unSelectedSource[i])) {
                    return i + selectedSource.size
                }
            }
            return -1
        }

        fun indexOf(entity: Entity<T>) = indexOf { it == entity }

        fun indexOf(tag: T) = indexOf { it.tag == tag }

        fun requireList(): MutableList<Entity<T>> {
            return pool.removeAt(pool.lastIndex)
        }

        private fun recycle(list: MutableList<Entity<T>>) {
            list.clear()
            pool.add(list)
        }

        fun clear() {
            selectedSource.clear()
            unSelectedSource.clear()
            splitPoint = 0
            backUp?.apply {
                recycle(unSelected)
                recycle(selected)
            }
            backUp = null
        }

        inner class BackUp(var selected: MutableList<Entity<T>>,
                                   var unSelected: MutableList<Entity<T>>,
                                   var splitPoint: Int)
    }
}