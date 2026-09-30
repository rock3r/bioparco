package dev.sebastiano.bioparco.tracing

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TracingTest {
    private val events = mutableListOf<String>()
    private val sink =
        object : Tracing.Sink {
            override fun begin(name: String): Any = name.also { events += "begin $it" }

            override fun end(section: Any?) {
                events += "end $section"
            }
        }

    @AfterTest fun uninstall() = Tracing.install(null)

    @Test
    fun `off by default, sections only run their block`() {
        assertFalse(Tracing.isEnabled)
        assertEquals(42, Tracing.section("work") { 42 })
        assertTrue(events.isEmpty())
    }

    @Test
    fun `an installed sink sees nested sections in order`() {
        Tracing.install(sink)
        assertTrue(Tracing.isEnabled)
        val result = Tracing.section("frame") { Tracing.section("pass") { "done" } }
        assertEquals("done", result)
        assertEquals(listOf("begin frame", "begin pass", "end pass", "end frame"), events)
    }

    @Test
    fun `a section ends even when its block throws`() {
        Tracing.install(sink)
        assertFailsWith<IllegalStateException> { Tracing.section("boom") { error("no") } }
        assertEquals(listOf("begin boom", "end boom"), events)
    }

    @Test
    fun `the trace directory comes from the property first, then the environment`() {
        assertEquals("/p", traceDirectory(property = "/p", environment = "/e"))
        assertEquals("/e", traceDirectory(property = null, environment = "/e"))
        assertEquals("/e", traceDirectory(property = " ", environment = "/e"))
        assertEquals(null, traceDirectory(property = null, environment = ""))
    }
}
