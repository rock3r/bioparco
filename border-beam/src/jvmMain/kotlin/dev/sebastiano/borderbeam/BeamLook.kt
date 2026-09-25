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
                BeamLook(0.16f, 0.32f, 0.42f, 1.8f, 1.3f, darkInk = true)
            } else {
                // Mid-high bloom so strength 1 reads as rim light; stroke up from CSS
                // 0.46 so the traveling core survives Skia AA on a thin ring.
                BeamLook(0.58f, 0.30f, 0.62f, 1.25f, 1.3f, darkInk = false)
            }
        BeamSize.Md ->
            if (light) {
                BeamLook(0.16f, 0.30f, 0.50f, 1.5f, 1.3f, darkInk = true)
            } else {
                BeamLook(0.42f, 0.48f, 0.68f, 1.25f, 1.3f, darkInk = false)
            }
        BeamSize.Line ->
            if (light) {
                BeamLook(0.22f, 0.36f, 0.48f, 1.95f, 1.3f, darkInk = true)
            } else {
                BeamLook(1.14f, 0.75f, 0.85f, 1.25f, 1.3f, darkInk = false)
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
