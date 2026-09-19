package dev.sebastiano.bioparco.recordings

import java.awt.GraphicsEnvironment
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

class SpecimenWindowProcessTest {
    @Test
    @Tag("recording")
    fun stopDoesNotExitTheProcess() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Needs a real AWT display")
        val java = "${System.getProperty("java.home")}/bin/java"
        val classpath = System.getProperty("java.class.path")
        val process =
            ProcessBuilder(
                    java,
                    "-Djava.awt.headless=false",
                    "-cp",
                    classpath,
                    SpecimenWindowProcessProbe.MAIN,
                )
                .redirectErrorStream(true)
                .start()
        val finished = process.waitFor(45, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            process.waitFor(2, TimeUnit.SECONDS)
        }
        val output = process.inputStream.bufferedReader().readText()
        assertTrue(finished, "process-probe timed out. output=$output")
        assertEquals(0, process.exitValue(), "process-probe exited ${process.exitValue()}: $output")
        assertTrue(
            output.contains(SpecimenWindowProcessProbe.STILL_ALIVE),
            "SpecimenWindow.stop() exited the JVM (Compose application default). " +
                "Later recordings never run. output=$output",
        )
    }
}
