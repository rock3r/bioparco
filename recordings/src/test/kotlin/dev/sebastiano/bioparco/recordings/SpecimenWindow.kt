package dev.sebastiano.bioparco.recordings

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Spawns a titled Compose Desktop window on a daemon thread so Spectre can attach and
 * [AutoRecorder] can capture it.
 */
internal class SpecimenWindow(
    private val title: String,
    private val size: DpSize,
    private val content: @Composable () -> Unit,
) {
    private val started = CountDownLatch(1)
    private val startupError = AtomicReference<Throwable?>(null)
    @Volatile private var exitFn: (() -> Unit)? = null
    private lateinit var thread: Thread

    fun requireDisplay() {
        check(!GraphicsEnvironment.isHeadless()) { "Specimen recordings need a real AWT display" }
    }

    fun start() {
        requireDisplay()
        thread =
            Thread(
                    {
                        try {
                            // Default application() calls exitProcess(0) when the window closes,
                            // which kills the Gradle test worker after the first enclosure.
                            application(exitProcessOnExit = false) {
                                exitFn = ::exitApplication
                                Window(
                                    onCloseRequest = ::exitApplication,
                                    title = title,
                                    state = rememberWindowState(size = size),
                                ) {
                                    content()
                                }
                                started.countDown()
                            }
                        } catch (error: Throwable) {
                            startupError.compareAndSet(null, error)
                            started.countDown()
                            throw error
                        }
                    },
                    "bioparco-specimen-$title",
                )
                .apply { isDaemon = true }
        thread.start()
        check(started.await(20, TimeUnit.SECONDS)) { "Specimen window '$title' did not start" }
        startupError.get()?.let { throw it }
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (System.nanoTime() < deadline) {
            if (frameOrNull() != null) return
            Thread.sleep(50)
        }
        error("Specimen window '$title' never appeared in Frame.getFrames()")
    }

    fun frame(): Frame = frameOrNull() ?: error("No frame titled '$title'")

    fun stop() {
        exitFn?.invoke()
        if (::thread.isInitialized) {
            thread.join(15_000)
        }
    }

    private fun frameOrNull(): Frame? =
        Frame.getFrames().firstOrNull { it.isDisplayable && it.title == title }
}
