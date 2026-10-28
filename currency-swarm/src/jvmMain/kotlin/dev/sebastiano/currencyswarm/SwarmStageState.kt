package dev.sebastiano.currencyswarm

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.unit.IntSize
import dev.sebastiano.bioparco.tracing.Tracing
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Everything the stage animates. The dots move every frame, but only [frame] is state, and only the
 * canvas's draw phase reads it: the animation redraws, it never recomposes. The pointer and the
 * layout are plain fields for the same reason.
 */
@Stable
internal class SwarmStageState(dotCount: Int = DOT_COUNT) {
    val swarm = Swarm(dotCount)

    /** Each currency's sign as `x, y` pairs, one per dot. */
    private val shapes: List<FloatArray> =
        Tracing.section("sample glyphs") {
            val random = Random(SAMPLE_SEED)
            Currencies.map { GlyphOutline.of(it.symbol).sample(dotCount, random) }
        }

    /** Which currency the dots are spelling, or heading for. */
    val index = mutableIntStateOf(0)

    /** The time of the last frame stepped, in frame-clock nanoseconds. The canvas reads this. */
    val frame = mutableLongStateOf(0L)

    /** Seconds since the first frame. */
    var seconds = 0f
        private set

    /** Pixels per glyph unit, and where the glyph's origin is, in the stage. */
    var scale = 1f
        private set

    var centre = Offset.Zero
        private set

    private var pointer = Offset.Unspecified
    private var firstFrameNanos = -1L

    init {
        swarm.place(cloud(dotCount, CLOUD_RADIUS, Random(CLOUD_SEED)))
    }

    fun onSize(size: IntSize) {
        centre = Offset(size.width / 2f, size.height / 2f)
        scale = minOf(size.width * WIDTH_SHARE, size.height * HEIGHT_SHARE)
    }

    /** The pointer moved to [position], in stage pixels. */
    fun onPointer(position: Offset) {
        pointer = (position - centre) / scale
    }

    fun onPointerExit() {
        pointer = Offset.Unspecified
    }

    fun step(frameNanos: Long) {
        if (firstFrameNanos < 0) firstFrameNanos = frameNanos
        seconds = (frameNanos - firstFrameNanos) / NANOS_PER_SECOND
        Tracing.section("swarm step") { swarm.step(seconds, pointer) }
        frame.longValue = frameNanos
    }

    /**
     * Sends the dots to the sign of [index], from wherever they are now. Pairing dots with their
     * new places sorts thousands of points, a few milliseconds while the JIT is cold, so it runs
     * off the frame, on a snapshot; the flights start on the frame after.
     */
    suspend fun morph() {
        val targets = shapes[index.intValue]
        val from = swarm.snapshot()
        val pairs =
            withContext(Dispatchers.Default) {
                Tracing.section("swarm pairing") { pairAlongCurve(from, targets) }
            }
        Tracing.section("swarm morph") { swarm.morphTo(targets, seconds, pairs) }
    }

    /** Suspends until the frame clock reaches [target] seconds. */
    suspend fun awaitSeconds(target: Float) {
        while (seconds < target) withFrameNanos {}
    }

    /** Suspends while the pointer is over the dots: a sign does not change under the hand. */
    suspend fun awaitPointerAway() {
        while (pointer.isSpecified) withFrameNanos {}
    }

    fun select(newIndex: Int) {
        index.intValue = Math.floorMod(newIndex, Currencies.size)
    }

    fun next() = select(index.intValue + 1)

    fun previous() = select(index.intValue - 1)

    companion object {
        /** About as many as the original shows. */
        const val DOT_COUNT = 2_600
        private const val CLOUD_RADIUS = 0.6f
        private const val CLOUD_SEED = 17
        private const val SAMPLE_SEED = 23
        private const val NANOS_PER_SECOND = 1_000_000_000f

        // A cap height of 40% of the width or 55% of the height, whichever is smaller: the tallest
        // sign, $, then still clears the caption.
        private const val WIDTH_SHARE = 0.4f
        private const val HEIGHT_SHARE = 0.55f
    }
}
