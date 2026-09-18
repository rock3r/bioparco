package dev.sebastiano.grabbystepper

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay

@Composable
actual fun RecomposeProbe(tag: String, enabled: Boolean) {
    if (!enabled) return
    val total = remember(tag) { AtomicInteger(0) }
    val window = remember(tag) { AtomicInteger(0) }
    SideEffect {
        total.incrementAndGet()
        window.incrementAndGet()
    }
    LaunchedEffect(tag) {
        while (true) {
            delay(1_000)
            val w = window.getAndSet(0)
            System.err.println("[recompose] $tag total=${total.get()} last1s=$w")
        }
    }
}
