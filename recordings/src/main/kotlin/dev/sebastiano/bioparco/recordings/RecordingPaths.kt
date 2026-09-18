package dev.sebastiano.bioparco.recordings

import java.nio.file.Path

object RecordingPaths {
    const val GRABBY_STEPPER = "grabby-stepper.mp4"
    const val CHAT_BUBBLE = "chat-bubble-transition.mp4"

    fun directory(explicit: String? = System.getProperty("bioparco.recordings.dir")): Path {
        val value = explicit?.trim().orEmpty()
        return if (value.isEmpty()) Path.of("build", "recordings") else Path.of(value)
    }

    fun file(name: String, directory: Path = directory()): Path = directory.resolve(name)
}
