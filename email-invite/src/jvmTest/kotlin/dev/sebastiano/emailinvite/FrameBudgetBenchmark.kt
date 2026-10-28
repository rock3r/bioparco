package dev.sebastiano.emailinvite

import dev.sebastiano.bioparco.tracing.TRACE_DIR_ENVIRONMENT
import dev.sebastiano.bioparco.tracing.Tracing
import dev.sebastiano.bioparco.tracing.startTracing
import java.io.File
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue

/**
 * Traces whole frames of the specimen drawn in software, to check they fit a 60 Hz frame. Skipped
 * unless `BIOPARCO_TRACE_DIR` names a directory:
 * ```
 * BIOPARCO_TRACE_DIR=/tmp/traces ./gradlew :email-invite:jvmTest --tests '*FrameBudgetBenchmark*' --rerun
 * python3 tracing/tools/trace-summary.py /tmp/traces/email-invite-benchmark
 * ```
 *
 * An `ImageComposeScene` draws every frame from scratch onto a CPU surface, so each `scenario …`
 * section is composition, layout, drawing and Skia's rasterising together, blurs included: the most
 * a software frame of this specimen can cost. A window redraws less.
 */
class FrameBudgetBenchmark {
    private val directory: String? = System.getenv(TRACE_DIR_ENVIRONMENT)

    @Test
    fun appFrames() {
        assumeTrue("Set $TRACE_DIR_ENVIRONMENT to run the benchmark", directory != null)
        val tracing = startTracing(File(directory, "email-invite-benchmark"))
        try {
            // No window here, so the scene can have a thread of its own.
            Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { scene ->
                runBlocking {
                    // Cold, Compose and Skiko run slowly for their first few thousand frames while
                    // the JIT compiles them. Play the scenario a few times first to measure the
                    // steady state.
                    repeat(WARM_UP_PASSES) {
                        playInvite(scene, gapMillis = 1) { _, render -> render() }
                    }
                    // A frame's worth of wall clock between frames, so the Sent hold and the close
                    // take their real length.
                    playInvite(scene, gapMillis = FRAME_MILLIS) { scenario, render ->
                        Tracing.section("scenario $scenario") { render() }
                    }
                }
            }
        } finally {
            tracing.close()
        }
    }

    private companion object {
        const val WARM_UP_PASSES = 6
        const val FRAME_MILLIS = 16L
    }
}
