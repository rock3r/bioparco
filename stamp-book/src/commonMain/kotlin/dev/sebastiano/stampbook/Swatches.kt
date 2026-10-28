package dev.sebastiano.stampbook

/** The swatch [step] places along from [index], wrapping round at either end of [count]. */
fun cycled(index: Int, step: Int, count: Int): Int = ((index + step) % count + count) % count
