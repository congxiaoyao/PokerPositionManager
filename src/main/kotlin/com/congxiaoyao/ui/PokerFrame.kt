package com.congxiaoyao.ui

import com.congxiaoyao.util.PokerConfig
import java.awt.Point
import java.awt.event.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import javax.swing.JFrame
import javax.swing.JOptionPane
import kotlin.concurrent.withLock
import kotlin.system.exitProcess

class PokerFrame : JFrame() {

    val panel = PokerPanel(this)

    init {
        addKeyListener(ExitListener())
        addKeyListener(SaveDetector(this))
        DragWindowListener(this).apply {
            addMouseMotionListener(this)
            addMouseListener(this)
        }
        setSize(800, 600)
        setLocation(500, 260)
        defaultCloseOperation = EXIT_ON_CLOSE;
        add(panel)
    }

    class DragWindowListener(private val jFrame: JFrame) : MouseMotionAdapter(), MouseListener {
        private var lastPoint: Point? = null
        override fun mouseReleased(e: MouseEvent?) {}
        override fun mouseEntered(e: MouseEvent?) {}
        override fun mouseClicked(e: MouseEvent?) {}
        override fun mouseExited(e: MouseEvent?) {}
        override fun mousePressed(e: MouseEvent) {
            lastPoint = e.locationOnScreen
        }
        override fun mouseDragged(e: MouseEvent) {
            val point = lastPoint ?: return
            val dx = e.locationOnScreen.x - point.x
            val dy = e.locationOnScreen.y - point.y
            jFrame.setLocation(jFrame.location.x + dx, jFrame.location.y + dy)
            lastPoint = e.locationOnScreen
        }
    }

    class ExitListener : KeyAdapter() {
        private var isCmdPressed = false
        override fun keyReleased(e: KeyEvent?) {
            isCmdPressed = false
        }

        override fun keyPressed(e: KeyEvent) {
            if (e.keyCode == 157) isCmdPressed = true
            if (e.keyChar == 'w' && isCmdPressed) {
                exitProcess(0)
            }
        }
    }

    class MoveDetector(
        private val slop: Int,
        private val interval: Long,
        private val moveListener: MoveListener
    ) : KeyAdapter() ,Runnable{
        private val pressedKeys = mutableSetOf<Int>()
        private val lock = ReentrantLock()
        private val executor = Executors.newSingleThreadScheduledExecutor()
        private var future: ScheduledFuture<*>? = null

        override fun keyPressed(e: KeyEvent) {
            lock.withLock {
                val shouldStartSchedule = pressedKeys.isEmpty()
                pressedKeys.add(e.keyCode)
                if (shouldStartSchedule) {
                    future = executor.scheduleWithFixedDelay(this, 0, interval, TimeUnit.MILLISECONDS)
                }
            }
        }

        override fun keyReleased(e: KeyEvent) {
            lock.withLock {
                pressedKeys.remove(e.keyCode)
                if (pressedKeys.isEmpty()) {
                    future?.cancel(false)
                }
                moveListener.onReleased()
            }
        }

        override fun run() {
            var dx = 0
            var dy = 0
            var isShiftDown = false
            lock.withLock {
                pressedKeys.forEach { code ->
                    when (code) {
                        KeyEvent.VK_LEFT -> dx -= slop
                        KeyEvent.VK_RIGHT -> dx += slop
                        KeyEvent.VK_UP -> dy -= slop
                        KeyEvent.VK_DOWN -> dy += slop
                    }
                }
                isShiftDown = pressedKeys.contains(KeyEvent.VK_SHIFT)
            }
            if (dx != 0 || dy != 0) {
                moveListener.onMove(dx, dy, isShiftDown)
            }
        }
    }

    class SaveDetector(private val frame: PokerFrame) : KeyAdapter() {
        private var isCmdPressed = false
        override fun keyReleased(e: KeyEvent?) {
            isCmdPressed = false
        }

        override fun keyPressed(e: KeyEvent) {
            if (e.keyCode == 157) isCmdPressed = true
            if (e.keyChar == 's' && isCmdPressed) {
                saveConfigToFile()
            }
        }

        private fun saveConfigToFile() {
            val pokers = frame.panel.bodies.map { it.content }
            val selected = frame.panel.bodies.filter { it.isSelect }.map { it.content }
            val highlights = frame.panel.bodies.filter { it.isHighLight }.map { it.content }
            val success = try {
                PokerConfig(pokers, selected, highlights).save()
                true
            } catch (e: Exception) {
                false
            }
            JOptionPane.showMessageDialog(frame, if (success) "完成！" else "失败！", "", JOptionPane.ERROR_MESSAGE);
        }
    }
}

interface MoveListener {
    fun onMove(dx: Int, dy: Int, isShiftDown: Boolean)
    fun onReleased()
}

