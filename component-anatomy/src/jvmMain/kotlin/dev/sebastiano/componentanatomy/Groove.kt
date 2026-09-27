package dev.sebastiano.componentanatomy

import java.util.concurrent.atomic.AtomicBoolean
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.random.Random

/** Tempo of the choreography, with or without music. */
const val BEATS_PER_SECOND = 2.0

/**
 * A small house groove, synthesised in memory, in stereo: a four-on-the-floor kick, a filtered
 * clap, off-beat open hats and swung closed hats, an off-beat sub bass, FM electric-piano stabs and
 * a soft pad over Fmaj9, Em9, Dm9, Cmaj9. Everything but the drums ducks under the kick.
 *
 * It is exactly one [AnatomyTimeline] loop long, so the music and the choreography loop together.
 * There are no audio files, so there is nothing to license.
 */
object GrooveSynth {
    const val SAMPLE_RATE = 44_100
    const val CHANNELS = 2
    private const val STEPS_PER_BEAT = 4
    private const val STEPS_PER_BAR = STEPS_PER_BEAT * AnatomyTimeline.BEATS_PER_BAR

    // A 16th note is 5512.5 samples. Keep the half: rounding it away would make the loop 64
    // samples short, and the music would drift off the picture a little more on every loop.
    private const val SAMPLES_PER_STEP = SAMPLE_RATE / BEATS_PER_SECOND / STEPS_PER_BEAT
    private const val SAMPLES_PER_BEAT = SAMPLE_RATE / BEATS_PER_SECOND

    /** Frames in one loop. */
    val frames = (SAMPLES_PER_STEP * STEPS_PER_BAR * AnatomyTimeline.BARS_PER_LOOP).toInt()

    private val bassRoots = doubleArrayOf(87.31, 82.41, 73.42, 65.41)
    private val voicings =
        arrayOf(
            doubleArrayOf(174.61, 220.00, 261.63, 329.63, 392.00),
            doubleArrayOf(164.81, 196.00, 246.94, 293.66, 369.99),
            doubleArrayOf(146.83, 174.61, 220.00, 261.63, 329.63),
            doubleArrayOf(130.81, 164.81, 196.00, 246.94, 293.66),
        )

    /** Renders one loop as interleaved 16-bit stereo. */
    fun render(): ShortArray {
        val mix = Mix(frames)
        val noise = Random(seed = 11)
        for (bar in 0 until AnatomyTimeline.BARS_PER_LOOP) {
            val chord = bar % voicings.size
            for (step in 0 until STEPS_PER_BAR) {
                val at = stepStart(bar * STEPS_PER_BAR + step)
                if (step % STEPS_PER_BEAT == 0) kick(mix, at, noise)
                if (step == 4 || step == 12) clap(mix, at, noise)
                if (step % 4 == 2) hat(mix, at, open = true, noise = noise)
                if (step % 2 == 1) hat(mix, at, open = false, noise = noise)
                if (step % 4 == 2) bass(mix, at, bassRoots[chord])
                if (step == 15 && bar % 2 == 1) bass(mix, at, bassRoots[chord] * 2)
                keysFor(step, bar)?.let { length -> keys(mix, at, voicings[chord], length) }
            }
            pad(mix, stepStart(bar * STEPS_PER_BAR), voicings[chord])
        }
        return mix.master()
    }

    /** Where a 16th note starts. Every other 16th comes a little late: that is the swing. */
    private fun stepStart(step: Int): Int {
        val swing = if (step % 2 == 1) SWING * SAMPLES_PER_STEP else 0.0
        return (step * SAMPLES_PER_STEP + swing).roundToInt()
    }

    /** Electric-piano stabs: a long chord on the one, short ones pushing the groove. */
    private fun keysFor(step: Int, bar: Int): Double? =
        when {
            step == 0 -> LONG_STAB
            step == 7 || step == 10 -> SHORT_STAB
            step == 14 && bar % 2 == 0 -> SHORT_STAB
            else -> null
        }

    private fun kick(mix: Mix, at: Int, noise: Random) {
        var phase = 0.0
        for (i in 0 until seconds(0.35)) {
            val t = time(i)
            phase += 2 * PI * (52 + 110 * exp(-t * 38)) / SAMPLE_RATE
            val body = sin(phase) * exp(-t * 11)
            val click = if (t < 0.002) (noise.nextDouble() * 2 - 1) * 0.3 * (1 - t / 0.002) else 0.0
            mix.drums(at + i, (body + click) * 0.95, pan = 0.0)
        }
    }

    private fun clap(mix: Mix, at: Int, noise: Random) {
        val filter = Biquad.bandPass(1_400.0, q = 1.1)
        for (i in 0 until seconds(0.35)) {
            val t = time(i)
            // Three quick attacks, the way hands never quite clap together, then a short tail.
            val burst = if (t < 0.03) exp(-(t % 0.011) * 190) else 0.0
            val tail = exp(-(t - 0.02).coerceAtLeast(0.0) * 16) * if (t >= 0.02) 0.6 else 0.0
            val sample = filter.process(noise.nextDouble() * 2 - 1) * (burst + tail) * 0.55
            mix.drums(at + i, sample, pan = 0.05)
            mix.send(at + i, sample * 0.5)
        }
    }

    private fun hat(mix: Mix, at: Int, open: Boolean, noise: Random) {
        val filter = Biquad.highPass(if (open) 7_000.0 else 9_000.0, q = 0.7)
        val decay = if (open) 11.0 else 60.0
        val level = if (open) 0.22 else 0.1
        for (i in 0 until seconds(if (open) 0.3 else 0.08)) {
            val sample = filter.process(noise.nextDouble() * 2 - 1) * exp(-time(i) * decay) * level
            mix.drums(at + i, sample, pan = if (open) 0.25 else -0.35)
        }
    }

    /** A warm sub: a sine with a little second and third harmonic, so it reads on laptops too. */
    private fun bass(mix: Mix, at: Int, frequency: Double) {
        var phase = 0.0
        val length = seconds(0.24)
        for (i in 0 until length) {
            val t = time(i)
            phase += 2 * PI * frequency / SAMPLE_RATE
            val tone = sin(phase) + 0.35 * sin(2 * phase) + 0.12 * sin(3 * phase)
            val envelope =
                (t / 0.004).coerceAtMost(1.0) * (1 - (i.toDouble() / length).let { it * it * it })
            mix.music(at + i, tanh(tone * 1.2) * envelope * 0.3, pan = 0.0)
        }
    }

    /**
     * Two-operator FM, the classic electric-piano recipe: a sine modulating a sine at the same
     * pitch, with the modulation fading fast, plus a quiet high tine. Each note plays twice,
     * slightly detuned left and right, for a soft chorus.
     */
    private fun keys(mix: Mix, at: Int, chord: DoubleArray, length: Double) {
        val frames = seconds(length + 0.9)
        for (note in chord) {
            for ((detune, pan) in listOf(0.9975 to -0.6, 1.0025 to 0.6)) {
                val f = note * detune
                for (i in 0 until frames) {
                    val t = time(i)
                    val index = 1.6 * exp(-t * 7) + 0.2
                    val modulator = sin(2 * PI * f * t)
                    val tine = 0.07 * sin(2 * PI * f * 7 * t) * exp(-t * 30)
                    val carrier = sin(2 * PI * f * t + index * modulator) + tine
                    val release = if (t < length) 1.0 else exp(-(t - length) * 14)
                    val envelope = (t / 0.003).coerceAtMost(1.0) * exp(-t * 2.4) * release
                    val sample = carrier * envelope * 0.075
                    mix.music(at + i, sample, pan)
                    mix.send(at + i, sample * 0.35)
                }
            }
        }
    }

    /** A quiet bed of detuned saws under each bar, darkened by a low-pass filter. */
    private fun pad(mix: Mix, at: Int, chord: DoubleArray) {
        val bar = seconds(AnatomyTimeline.BEATS_PER_BAR / BEATS_PER_SECOND)
        val frames = bar + seconds(0.6)
        for ((index, note) in chord.drop(2).withIndex()) {
            val pan = (index - 1.0) * 0.5
            val filter = Biquad.lowPass(1_100.0, q = 0.6)
            val phases = DoubleArray(3)
            val detunes = doubleArrayOf(0.996, 1.0, 1.004)
            for (i in 0 until frames) {
                var saw = 0.0
                for (voice in detunes.indices) {
                    phases[voice] = (phases[voice] + note * detunes[voice] / SAMPLE_RATE) % 1.0
                    saw += 2 * phases[voice] - 1
                }
                val t = time(i)
                val swell = (t / 0.5).coerceAtMost(1.0)
                val release = if (i < bar) 1.0 else exp(-(i - bar).toDouble() / SAMPLE_RATE * 8)
                mix.music(at + i, filter.process(saw / 3) * swell * release * 0.05, pan)
            }
        }
    }

    private fun seconds(value: Double) = (value * SAMPLE_RATE).toInt()

    private fun time(frame: Int) = frame.toDouble() / SAMPLE_RATE

    /**
     * Three buses: drums, music (which ducks under the kick) and a reverb send. Everything wraps
     * round the loop, so a tail that passes the end is heard at the start.
     */
    private class Mix(val frames: Int) {
        private val drumsL = DoubleArray(frames)
        private val drumsR = DoubleArray(frames)
        private val musicL = DoubleArray(frames)
        private val musicR = DoubleArray(frames)
        private val sendBus = DoubleArray(frames)

        fun drums(frame: Int, sample: Double, pan: Double) = add(drumsL, drumsR, frame, sample, pan)

        fun music(frame: Int, sample: Double, pan: Double) = add(musicL, musicR, frame, sample, pan)

        fun send(frame: Int, sample: Double) {
            sendBus[frame % frames] += sample
        }

        private fun add(
            left: DoubleArray,
            right: DoubleArray,
            frame: Int,
            sample: Double,
            pan: Double,
        ) {
            val index = frame % frames
            left[index] += sample * (1 - pan)
            right[index] += sample * (1 + pan)
        }

        fun master(): ShortArray {
            val (wetL, wetR) = Reverb().process(sendBus)
            val out = ShortArray(frames * CHANNELS)
            for (i in 0 until frames) {
                val duck = duckAt(i)
                val left = drumsL[i] + musicL[i] * duck + wetL[i] * WET
                val right = drumsR[i] + musicR[i] * duck + wetR[i] * WET
                out[2 * i] = toShort(left)
                out[2 * i + 1] = toShort(right)
            }
            return out
        }

        /** The side-chain pump: the music dips on every kick and breathes back in. */
        private fun duckAt(frame: Int): Double {
            val sinceKick = (frame % SAMPLES_PER_BEAT) / SAMPLE_RATE
            val attack = (sinceKick / 0.004).coerceAtMost(1.0)
            return 1 - DUCK_DEPTH * attack * exp(-sinceKick * DUCK_RECOVERY)
        }

        private fun toShort(sample: Double) =
            (tanh(sample * DRIVE) * HEADROOM * Short.MAX_VALUE).toInt().toShort()
    }

    /**
     * A small Schroeder reverb: parallel damped combs into series all-passes, with slightly
     * different lengths per side. It runs over the loop twice and keeps the second pass, so the
     * tail at the start of the loop is the tail from its end.
     */
    private class Reverb {
        fun process(input: DoubleArray): Pair<DoubleArray, DoubleArray> =
            side(input, spread = 0) to side(input, spread = STEREO_SPREAD)

        private fun side(input: DoubleArray, spread: Int): DoubleArray {
            val combs = COMBS.map { Comb(it + spread) }
            val allPasses = ALL_PASSES.map { AllPass(it + spread) }
            val out = DoubleArray(input.size)
            repeat(2) { pass ->
                for (i in input.indices) {
                    var sample = combs.sumOf { it.process(input[i]) } / combs.size
                    for (allPass in allPasses) sample = allPass.process(sample)
                    if (pass == 1) out[i] = sample
                }
            }
            return out
        }

        private class Comb(length: Int) {
            private val buffer = DoubleArray(length)
            private var index = 0
            private var store = 0.0

            fun process(input: Double): Double {
                val output = buffer[index]
                store = output * (1 - DAMP) + store * DAMP
                buffer[index] = input + store * FEEDBACK
                index = (index + 1) % buffer.size
                return output
            }
        }

        private class AllPass(length: Int) {
            private val buffer = DoubleArray(length)
            private var index = 0

            fun process(input: Double): Double {
                val delayed = buffer[index]
                buffer[index] = input + delayed * 0.5
                index = (index + 1) % buffer.size
                return delayed - input
            }
        }

        private companion object {
            val COMBS = listOf(1_116, 1_188, 1_277, 1_356, 1_422, 1_491)
            val ALL_PASSES = listOf(556, 441, 341)
            const val STEREO_SPREAD = 23
            const val FEEDBACK = 0.8
            const val DAMP = 0.35
        }
    }

    /** An RBJ-cookbook biquad filter, for the clap, hats and pad. */
    private class Biquad(
        private val b0: Double,
        private val b1: Double,
        private val b2: Double,
        private val a1: Double,
        private val a2: Double,
    ) {
        private var x1 = 0.0
        private var x2 = 0.0
        private var y1 = 0.0
        private var y2 = 0.0

        fun process(x: Double): Double {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = x
            y2 = y1
            y1 = y
            return y
        }

        companion object {
            fun lowPass(cutoff: Double, q: Double) =
                make(cutoff, q) { c -> Triple((1 - c) / 2, 1 - c, (1 - c) / 2) }

            fun highPass(cutoff: Double, q: Double) =
                make(cutoff, q) { c -> Triple((1 + c) / 2, -(1 + c), (1 + c) / 2) }

            fun bandPass(centre: Double, q: Double): Biquad {
                val w = 2 * PI * centre / SAMPLE_RATE
                val alpha = sin(w) / (2 * q)
                val a0 = 1 + alpha
                return Biquad(alpha / a0, 0.0, -alpha / a0, -2 * cos(w) / a0, (1 - alpha) / a0)
            }

            private fun make(
                cutoff: Double,
                q: Double,
                numerator: (Double) -> Triple<Double, Double, Double>,
            ): Biquad {
                val w = 2 * PI * cutoff / SAMPLE_RATE
                val cosW = cos(w)
                val alpha = sin(w) / (2 * q)
                val a0 = 1 + alpha
                val (b0, b1, b2) = numerator(cosW)
                return Biquad(b0 / a0, b1 / a0, b2 / a0, -2 * cosW / a0, (1 - alpha) / a0)
            }
        }
    }

    private const val SWING = 0.16
    private const val LONG_STAB = 0.9
    private const val SHORT_STAB = 0.16
    private const val WET = 0.9
    private const val DUCK_DEPTH = 0.6
    private const val DUCK_RECOVERY = 7.0
    private const val DRIVE = 1.05
    private const val HEADROOM = 0.85
}

/**
 * Plays the groove in a loop and reports where it is, in beats. The beat comes from the audio
 * line's own frame counter, so the picture follows what you hear.
 */
class GroovePlayer(private val pcm: ShortArray) {
    private var running: AtomicBoolean? = null
    @Volatile private var line: SourceDataLine? = null
    private var startBeat = 0.0

    fun start(fromBeat: Double) {
        stop()
        val output = AudioSystem.getSourceDataLine(FORMAT)
        output.open(FORMAT, BUFFER_FRAMES * FRAME_BYTES)
        output.start()
        startBeat = fromBeat
        line = output
        val frames = pcm.size / GrooveSynth.CHANNELS
        val loopBeats = AnatomyTimeline.BEATS_PER_LOOP.toDouble()
        val beatInLoop = ((fromBeat % loopBeats) + loopBeats) % loopBeats
        var cursor = (beatInLoop / BEATS_PER_SECOND * GrooveSynth.SAMPLE_RATE).toInt() % frames
        val running = AtomicBoolean(true)
        this.running = running
        Thread(
                {
                    val chunk = ByteArray(CHUNK_FRAMES * FRAME_BYTES)
                    while (running.get()) {
                        for (i in 0 until CHUNK_FRAMES) {
                            for (channel in 0 until GrooveSynth.CHANNELS) {
                                val sample = pcm[cursor * GrooveSynth.CHANNELS + channel].toInt()
                                val at = i * FRAME_BYTES + channel * 2
                                chunk[at] = sample.toByte()
                                chunk[at + 1] = (sample shr 8).toByte()
                            }
                            cursor = (cursor + 1) % frames
                        }
                        output.write(chunk, 0, chunk.size)
                    }
                    // The writer owns the line, so closing it never blocks the UI thread.
                    output.stop()
                    output.flush()
                    output.close()
                },
                "component-anatomy-groove",
            )
            .apply {
                isDaemon = true
                start()
            }
    }

    /** The beat you are hearing now, or null when nothing plays. */
    fun beats(): Double? {
        val output = line ?: return null
        return startBeat +
            output.longFramePosition.toDouble() / GrooveSynth.SAMPLE_RATE * BEATS_PER_SECOND
    }

    /** Asks the writer thread to finish and close the line. Returns at once. */
    fun stop() {
        running?.set(false)
        running = null
        line = null
    }

    companion object {
        private const val BUFFER_FRAMES = 4_096
        private const val CHUNK_FRAMES = 1_024
        private const val FRAME_BYTES = 2 * GrooveSynth.CHANNELS
        private val FORMAT =
            AudioFormat(GrooveSynth.SAMPLE_RATE.toFloat(), 16, GrooveSynth.CHANNELS, true, false)

        /** Whether this machine has somewhere to play sound. CI and headless JVMs do not. */
        val isAvailable: Boolean by lazy {
            runCatching {
                    AudioSystem.isLineSupported(DataLine.Info(SourceDataLine::class.java, FORMAT))
                }
                .getOrDefault(false)
        }
    }
}
