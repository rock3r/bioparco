package dev.sebastiano.bioparco.recordings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * Child-JVM entry used by [SpecimenWindowProcessTest]. Prints [STILL_ALIVE] only if
 * [SpecimenWindow.stop] returns. Compose Desktop's `application {}` defaults to
 * `exitProcessOnExit = true`, which kills this process after the first enclosure and
 * leaves later README movies unwritten.
 */
internal object SpecimenWindowProcessProbe {
    const val MAIN = "dev.sebastiano.bioparco.recordings.SpecimenWindowProcessProbe"
    const val STILL_ALIVE = "BIOPARCO_SPECIMEN_WINDOW_STILL_ALIVE"

    @JvmStatic
    fun main(args: Array<String>) {
        val window =
            SpecimenWindow(title = "bioparco · process-probe", size = DpSize(240.dp, 240.dp)) {
                Box(Modifier.fillMaxSize())
            }
        window.start()
        window.stop()
        // Compose's default application() calls exitProcess after the scope ends.
        // Wait long enough that a leaked exit would have already killed this JVM.
        Thread.sleep(2_000)
        println(STILL_ALIVE)
    }
}
