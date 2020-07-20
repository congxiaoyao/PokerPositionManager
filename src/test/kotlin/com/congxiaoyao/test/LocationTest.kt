package com.congxiaoyao.test

import com.congxiaoyao.LocationManager
import com.congxiaoyao.TestLocationManager
import com.congxiaoyao.ui.Body
import com.congxiaoyao.ui.PokerFrame
import com.google.gson.Gson
import org.junit.Test
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.io.BufferedWriter
import java.io.File
import java.util.*
import javax.swing.JFrame
import javax.swing.JPanel
import kotlin.concurrent.thread
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime


@ExperimentalTime
class LocationTest {

    private val entities = listOf("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K")
    private var count = 0
    private var testCast = Duration.ZERO
    private var locationCast = Duration.ZERO

    private val file = File("res/record")
    private lateinit var writer: BufferedWriter
    private val gson = Gson()

    @Test
    fun playRecord() {
        val frame = TestFrame()
        frame.isVisible = true

        thread {
            val reader = file.bufferedReader()
            reader.lineSequence().drop(1000).forEach {
                val manager = gson.fromJson<TestLocationManager>(it, TestLocationManager::class.java)
                frame.locationManager = manager
                frame.panel.repaint()
                Thread.sleep(5)
            }
        }

        Thread.currentThread().join()
    }

    @Test
    fun moveTest() {
        count = 0
        writer = file.bufferedWriter(bufferSize = 1024 * 1024 * 64)
        permutations(entities.size) { start, len ->
            val element = mutableListOf<String>()
            repeat(len) {
                element.add(entities[start + it])
            }
            println(element)

            move(element, 1)
            createPrime(300).asSequence().forEach {
                move(element, it)
            }
        }
        writer.flush()
        writer.close()
        println(count)
        println("testCast = $testCast locationCast = $locationCast")
    }

    private fun move(selected: List<String>, dis: Int) {
        if (dis < 0) throw RuntimeException()

        val locationManager = LocationManager<String>(30, 100, 10)
        val testManager = TestLocationManager(30, 100, 10)
        val target = selected.sorted()
        entities.forEach {
            locationManager.addElement(it)
            testManager.addBean(it)
        }
        val beans = testManager.beans.filter { it.name in target }
        testManager.expand(beans)
        val entities = selected.mapNotNull { locationManager.findByTag(it) }

        val width = locationManager.width

        //开始移动

        //正向
        while (true) {
            move(locationManager, entities, beans, testManager, dis)
            if (testManager.selected!!.first().location > width) {
                break
            }
        }
        //负向
        while (true) {
            move(locationManager, entities, beans, testManager, -dis)
            if (testManager.selected!!.last().location < -testManager.elementWidth) {
                break
            }
        }
    }

    private fun move(locationManager: LocationManager<String>,
                     entities: List<LocationManager.Entity<String>>,
                     beans: List<TestLocationManager.Bean>,
                     testManager: TestLocationManager,
                     dis: Int) {
        locationCast += measureTime {
            locationManager.moveSelectedEntitiesOrg(entities, entities.first(), dis)
        }
        testCast += measureTime {
            testManager.moveSelectedEntities(beans, dis)
        }
        writer.appendln(gson.toJson(testManager))
        eq(locationManager, testManager)
    }

    private fun eq(locationManager: LocationManager<String>, testManager: TestLocationManager) {
        val result1 = locationManager.sequence.map { TestLocationManager.Bean(it.tag, it.location) }.toList()
        val result2 = testManager.beans
        if (result1 != result2) {
            throw RuntimeException()
        }
        count++
    }

    private fun permutations(n: Int, action: (start: Int, len: Int) -> Unit) {
        for (count in 1..n) {
            repeat(n - count + 1) {
                action(it, count)
            }
        }
    }

    fun createPrime(n: Int): List<Int> {
        val primes: MutableList<Int> = ArrayList()
        primes.add(2)
        var i = 3
        while (i <= n) {
            for (j in primes.indices) {
                if (i % primes[j] == 0) break
                if (j == primes.size - 1) {
                    primes.add(i)
                    break
                }
            }
            i += 2
        }
        return primes
    }

    class TestFrame : JFrame() {
        private val renderingHints = mapOf(
            RenderingHints.KEY_TEXT_ANTIALIASING to RenderingHints.VALUE_TEXT_ANTIALIAS_ON,
            RenderingHints.KEY_ANTIALIASING to RenderingHints.VALUE_ANTIALIAS_ON
        )

        var locationManager: TestLocationManager = TestLocationManager(0, 0, 0)

        val panel = object : JPanel() {
            override fun paint(g: Graphics?) {
                super.paint(g)
                (g as Graphics2D).setRenderingHints(renderingHints)

                val bodies = locationManager.beans.map {
                    Body.create(it.location, locationManager.elementWidth, it.name[0])
                }.toMutableList()

                locationManager.selected?.forEach { bean ->
                    val body = bodies.find { it.content == bean.name[0] } ?: return@forEach
                    body.isSelect = true
                }

                //bodies gravity
                val bodyHeight = bodies.firstOrNull()?.bounds?.height ?: return
                val panelHeight = height
                val bodiesWidth = locationManager.width
                val panelWidth = width
                g.translate((panelWidth - bodiesWidth) / 2, (panelHeight - bodyHeight) / 2)

                //update bounds
                locationManager.beans.forEach { bean ->
                    val body = bodies.find { it.content == bean.name[0] }!!
                    body.bounds.x = bean.location
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
            }
        }

        init {
            addKeyListener(PokerFrame.ExitListener())
            setSize(800, 600)
            setLocation(500, 260)
            defaultCloseOperation = EXIT_ON_CLOSE;

            add(panel)
        }
    }
}