package dev.sebastiano.thinkingorbs

enum class OrbDesign(val title: String, val summary: String) {
    Working("Working", "Particles follow tilted orbits."),
    Searching("Searching", "A scan line rotates around a dotted globe."),
    Solving("Solving", "Bands rotate out of alignment, then align again."),
    Listening("Listening", "A waveform moves through the rings."),
    Connecting("Connecting", "Lines connect points on a sphere."),
    Weaving("Weaving", "Three strands twist around the sphere."),
    Composing("Composing", "Several bands form a moving wave."),
    Breathing("Breathing", "A ring slowly changes shape."),
    Shaping("Shaping", "A circle becomes a triangle, then a square.");

    val accessibilityLabel: String
        get() = if (this == Breathing) "Thinking…" else "$title…"

    fun frame(size: OrbSize = OrbSize.Regular, atSeconds: Double): OrbFrame {
        val resolved = OrbPresets.resolve(this, size)
        return resolved.frame(size.points, atSeconds * resolved.speed)
    }
}

enum class OrbSize(val points: Double) {
    Regular(64.0),
    Small(20.0),
}

data class OrbDot(
    val x: Double,
    val y: Double,
    val z: Double,
    val radius: Double,
    val white: Double,
    val alpha: Double = 1.0,
)

data class OrbLine(
    val x1: Double,
    val y1: Double,
    val x2: Double,
    val y2: Double,
    val white: Double,
    val alpha: Double = 1.0,
    val width: Double,
)

data class OrbFrame(val dots: List<OrbDot>, val lines: List<OrbLine>)
