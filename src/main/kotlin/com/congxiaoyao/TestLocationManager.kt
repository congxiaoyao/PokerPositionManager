package com.congxiaoyao

import com.congxiaoyao.ui.Body

class TestLocationManager(
    val interval: Int,
    val elementWidth: Int,
    val extraMargin: Int = 0) {

    val beans = mutableListOf<Bean>()
    var selected: List<Bean>? = null

    fun <T> map(list: List<LocationManager.Entity<T>>): List<Bean> {
        return list.map {
            Bean((it.tag as Body).content.toString(), it.location)
        }
    }

    fun moveSelectedEntities(selected: List<Bean>, dis: Int) {
        this.selected = selected
        if (selected.isEmpty()) return
        val target = selected.sortedBy { it.location }
        val names = target.map { it.name }.toSet()
        val group = beans.filter { it.name !in names }.groupBy {
            if (it.location < target.first().location) 0
            else 1
        }
        val leftList = (group[0] ?: emptyList()).toMutableList()
        val rightList = (group[1] ?: emptyList()).toMutableList()

        target.forEach { it.location += dis }

        var changed = false
        var pointer = leftList.lastIndex
        while (pointer >= 0) {
            val bean = leftList[pointer]
            if (bean.location >= target.first().location) {
                leftList.removeAt(pointer)
                rightList.add(0, bean)
                changed = true
            }
            pointer--
        }

        if (!changed) {
            pointer = 0
            while (pointer < rightList.size) {
                val bean = rightList[pointer]
                if (bean.location <= target.last().location) {
                    rightList.removeAt(pointer--)
                    leftList.add(bean)
                }
                pointer++
            }

        }

        leftList.forEachIndexed { index, bean ->
            bean.location = index * interval - extraMargin
        }
        rightList.reversed().forEachIndexed { i, bean ->
            val index = beans.lastIndex - i
            bean.location = index * interval + extraMargin
        }

        val result = leftList + target + rightList
        beans.clear()
        beans.addAll(result)
    }

    fun expand(selected: List<Bean>) {
        if (selected.isEmpty()) return
        val target = selected.sortedBy { it.location }
        val group = beans.filter { it.name !in target.map { it.name } }.groupBy {
            if (it.location < target.first().location) 0
            else 1
        }
        val leftList = group[0] ?: emptyList()
        val rightList = group[1] ?: emptyList()

        leftList.forEachIndexed { index, bean ->
            bean.location = index * interval - extraMargin
        }
        rightList.reversed().forEachIndexed { i, bean ->
            val index = beans.lastIndex - i
            bean.location = index * interval + extraMargin
        }
    }

    fun addBean(name: String) {
        if (beans.isEmpty()) {
            beans.add(Bean(name, 0))
            return
        }
        val last = beans.last()
        beans.add(Bean(name, last.location + interval))
    }

    override fun toString(): String {
        return buildString {
            append("(")
            append(beans.first().toString())
            if (beans.size > 1) {
                beans.drop(1).forEach {
                    append(",")
                    append(it.toString())
                }
            }
            append(")")
        }
    }

    val width get() = if (beans.isEmpty()) 0 else (interval * beans.size - 1) + elementWidth

    data class Bean(var name: String, var location: Int) {
        override fun toString(): String {
            return "$name($location)"
        }
    }
}