package dev.sebastiano.thinkingorbs

enum class OrbDesign(val title: String, val summary: String) {
    Working("Working", "Particles on tilted orbits"),
    Searching("Searching", "A scan meridian sweeps a dotted globe"),
    Solving("Solving", "Bands scramble, then click back solved"),
    Listening("Listening", "A waveform rolls through the rings"),
    Connecting("Connecting", "A constellation wires itself"),
    Weaving("Weaving", "Three strands plait around the sphere"),
    Composing("Composing", "An undulating multi-band sash"),
    Breathing("Breathing", "A ring slowly morphing"),
    Shaping("Shaping", "Circle → triangle → square");

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
