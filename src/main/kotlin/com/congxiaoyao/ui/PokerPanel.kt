package com.congxiaoyao.ui

import com.congxiaoyao.LocationManager
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.JFrame
import javax.swing.JPanel


class PokerPanel(frame: JFrame) : JPanel() {

    companion object{
        private const val elementWidth = 100
        private const val interval = 30
        private val renderingHints = mapOf(
            RenderingHints.KEY_TEXT_ANTIALIASING to RenderingHints.VALUE_TEXT_ANTIALIAS_ON,
            RenderingHints.KEY_ANTIALIASING to RenderingHints.VALUE_ANTIALIAS_ON
        )
    }

    private val locationManager: LocationManager<Body> = LocationManager(interval, elementWidth,10)

    val bodies = mutableListOf<Body>()
    var moveRef = 0

    init {
        frame.addKeyListener(SelectPokerHandler())
        frame.addKeyListener(DeletePokerHandler())
        frame.addKeyListener(SpaceHandler())

        frame.addKeyListener(PokerFrame.MoveDetector(50, 160, object : MoveListener {
            override fun onMove(dx: Int, dy: Int, isShiftDown: Boolean) {
                if (isShiftDown) {
                    moveRef = dx
                } else {
                    val selected = bodies.filter { it.isSelect }.mapNotNull { locationManager.findByTag(it) }
                    val anchor = bodies.find { it.isHighLight }?.let { locationManager.findByTag(it) } ?: return
                    locationManager.moveSelectedEntities(selected, anchor, dx)
                    moveRef = 0
                }
                repaint()
            }

            override fun onReleased() {
                moveRef = 0
                repaint()
            }
        }))
    }

    override fun paint(g: Graphics) {
        super.paint(g)
        (g as Graphics2D).setRenderingHints(renderingHints)

        //bodies gravity
        val bodyHeight = bodies.firstOrNull()?.bounds?.height ?: return
        val panelHeight = height
        val bodiesWidth = locationManager.width
        val panelWidth = width
        g.translate((panelWidth - bodiesWidth) / 2, (panelHeight - bodyHeight) / 2)

        //update bounds
        locationManager.sequence.forEach {
            it.tag.bounds.x = it.location
        }
        bodies.sortBy { it.bounds.x }

        //draw bodies
        bodies.forEach {
            it.paint(g)
        }

        //draw indexes
        bodies.take(10).map { it.bounds }.forEachIndexed { index, bounds ->
            g.color = Color.RED
            g.drawString(
                ((index + 1) % 10).toString(),
                bounds.x + 10,
                bounds.y + bounds.height + 25
            )
        }

        if (moveRef < 0) {
            val bounds = bodies.first { it.isSelect }.bounds
            val x = bounds.x + moveRef
            val y = bounds.y - bounds.height/2
            g.drawLine(x, y, x, y + bounds.height * 2)
        } else if (moveRef > 0) {
            val bounds = bodies.last { it.isSelect }.bounds
            val x = bounds.x + moveRef
            val y = bounds.y - bounds.height/2
            g.drawLine(x, y, x, y + bounds.height * 2)
        }
    }

    fun addBody(char: Char) {
        val body = Body.create(0, elementWidth, char)
        locationManager.addElement(body)
        val location = locationManager.findByTag(body)?.location ?: return
        body.bounds.x = location
        bodies.add(body)
    }

    fun clearBodies() {
        locationManager.clear()
        bodies.clear()
    }

    inner class SelectPokerHandler : KeyAdapter() {
        private val numberCodes = 48..57
        override fun keyPressed(e: KeyEvent) {
            val keyCode = e.keyCode
            if (keyCode in numberCodes) {
                val index = ((keyCode - 48) + 9) % 10
                val body = bodies.getOrNull(index) ?: return
                if (e.isShiftDown) {
                    body.isHighLight = !body.isHighLight
                } else {
                    body.isSelect = !body.isSelect
                }
                repaint()
            }
        }
    }

    inner class DeletePokerHandler : KeyAdapter() {
        override fun keyPressed(e: KeyEvent) {
            if (e.keyCode == KeyEvent.VK_BACK_SPACE) {
                val selectedBodies = bodies.filter { it.isHighLight }
                bodies.removeAll(selectedBodies)
                selectedBodies.forEach {
                    locationManager.removeByTag(it)
                }
                repaint()
            }
        }
    }

    inner class SpaceHandler : KeyAdapter() {
        override fun keyPressed(e: KeyEvent) {
            if (e.keyCode == KeyEvent.VK_SPACE) {
                val selected = bodies.filter { it.isSelect }
                    .map { locationManager.findByTag(it) }
                    .filterNotNull()
                val anchor = bodies.find { it.isHighLight }?.let { locationManager.findByTag(it) } ?: return
                locationManager.moveSelectedEntities(selected, anchor,-10)
                repaint()
            }
        }
    }
}
