package com.congxiaoyao.ui

import java.awt.*
import java.awt.geom.RoundRectangle2D

class Body(val bounds: Rectangle, val content: Char) {

    companion object {
        private val STROKE = BasicStroke(1.5f)
        private val ARC = 10.0
        private val font = Font(null, Font.PLAIN, 16)

        fun create(location: Int, width: Int, content: Char): Body {
            val bounds = Rectangle(location, 0, width, 140)
            return Body(bounds, content)
        }
    }

    var isSelect = false
    var isHighLight = false

    private val roundRect = RoundRectangle2D.Float()
    private val yOffset get() = if (isSelect) -50 else 0

    fun paint(graphics2D: Graphics2D) {
        roundRect.setRoundRect(bounds.x.toDouble(),
            bounds.y.toDouble() + yOffset,
            bounds.width.toDouble(),
            bounds.height.toDouble(),
            ARC, ARC)

        graphics2D.color = Color(0xfafafa)
        graphics2D.fill(roundRect)

        graphics2D.color = if (isHighLight) Color(255, 96, 96) else Color.GRAY
        graphics2D.stroke = STROKE
        graphics2D.draw(roundRect)

        graphics2D.color = if (isHighLight) Color(255, 0, 0) else Color.BLACK
        graphics2D.font = font
        val padding = (ARC / 2).toInt()
        graphics2D.drawString(content.toString(), roundRect.x + padding, roundRect.y + font.size + padding)
    }
}