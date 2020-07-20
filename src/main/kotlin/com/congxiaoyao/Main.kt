package com.congxiaoyao

import com.congxiaoyao.ui.PokerFrame
import com.congxiaoyao.util.PokerConfig
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.lang.Exception

fun main() {
    val frame = PokerFrame()
    resetTestBodies(frame)

    frame.addKeyListener(object :KeyAdapter(){
        override fun keyPressed(e: KeyEvent) {
            if (e.keyCode == KeyEvent.VK_ESCAPE) {
                resetTestBodies(frame)
                frame.repaint()
            }
        }
    })
    frame.isVisible = true
}

private fun resetTestBodies(frame: PokerFrame) {
    frame.panel.clearBodies()
    try {
        val (pokers, selected, highlights) = PokerConfig.load()
        pokers.forEach {
            frame.panel.addBody(it)
        }
        selected.forEach { c ->
            frame.panel.bodies.first { it.content == c }.isSelect = true
        }
        frame.panel.syncSelectStateToLocationManager()
        highlights.forEach { c ->
            frame.panel.bodies.first { it.content == c }.isHighLight = true
        }
    } catch (e: Exception) {
        repeat(10) {
            frame.panel.addBody((it + 'A'.toInt()).toChar())
        }
    }
}

