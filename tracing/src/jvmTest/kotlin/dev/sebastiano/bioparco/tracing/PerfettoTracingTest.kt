package dev.sebastiano.bioparco.tracing

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PerfettoTracingTest {
    @Test
    fun `a trace with a section lands in the directory`() {
        val directory = Files.createTempDirectory("bioparco-trace").toFile()
        val tracing = startTracing(directory)
        assertTrue(Tracing.isEnabled)
        Tracing.section("hello") { Thread.sleep(2) }
        tracing.close()
        assertFalse(Tracing.isEnabled)
        val traces = directory.listFiles().orEmpty().filter { it.name.endsWith(".perfetto-trace") }
        assertTrue(traces.isNotEmpty(), "no trace in $directory")
        assertTrue(traces.single().readBytes().decodeToString().contains("hello"))
    }
}
