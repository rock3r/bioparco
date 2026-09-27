package dev.sebastiano.componentanatomy

/** Test tags for driving the specimen from Spectre. */
object AnatomyTags {
    const val STAGE = "component-anatomy-stage"
    const val OUTLINED = "component-anatomy-outlined"
    const val DEFAULT = "component-anatomy-default"
    const val EXPLODED = "component-anatomy-exploded"
    const val MUSIC = "component-anatomy-music"
    const val BACK_TO_GROOVIN = "component-anatomy-back-to-groovin"

    fun state(state: AnatomyState) = "component-anatomy-state-${state.name.lowercase()}"
}
