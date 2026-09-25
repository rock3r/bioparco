package dev.sebastiano.borderbeam

internal data class BeamLook(
    val stroke: Float,
    val inner: Float,
    val bloom: Float,
    val saturation: Float,
    val brightness: Float,
    val darkInk: Boolean,
)

internal fun beamLook(size: BeamSize, theme: BeamTheme): BeamLook {
    val light = theme == BeamTheme.Light
    return when (size) {
        BeamSize.Sm ->
            if (light) {
                BeamLook(0.22f, 0.40f, 0.55f, 1.8f, 1.3f, darkInk = true)
            } else {
                BeamLook(0.72f, 0.55f, 1f, 1.35f, 1.35f, darkInk = false)
            }
        BeamSize.Md ->
            if (light) {
                BeamLook(0.20f, 0.42f, 0.70f, 1.5f, 1.3f, darkInk = true)
            } else {
                BeamLook(0.62f, 0.78f, 1f, 1.35f, 1.28f, darkInk = false)
            }
        BeamSize.Line ->
            if (light) {
                BeamLook(0.28f, 0.45f, 0.55f, 1.95f, 1.3f, darkInk = true)
            } else {
                BeamLook(1.14f, 0.85f, 1f, 1.35f, 1.35f, darkInk = false)
            }
        BeamSize.PulseOutside ->
            if (light) {
                BeamLook(1.96f, 1.04f, 0.42f, 0.6f, 1.7f, darkInk = true)
            } else {
                BeamLook(0.94f, 0.34f, 0.30f, 1.2f, 1.9f, darkInk = false)
            }
        BeamSize.PulseInner ->
            if (light) {
                BeamLook(0.32f, 0.40f, 0.80f, 0.75f, 1.3f, darkInk = true)
            } else {
                BeamLook(1.54f, 0.44f, 0.66f, 1.2f, 0.75f, darkInk = false)
            }
    }
}
