package dev.sebastiano.expandingsearch

/** One thing to do to a row of the panel, named by its [key] and the [slot] it is in or goes to. */
sealed interface RowStep<K> {
    /** The row stays where it is; only its text may change. */
    data class Keep<K>(val key: K, val slot: Int) : RowStep<K>

    /** A row still fading out of this slot is wanted here again, so it fades back in. */
    data class Revive<K>(val key: K, val slot: Int) : RowStep<K>

    /** The row fades out where it is. */
    data class Leave<K>(val key: K, val slot: Int) : RowStep<K>

    /** A row fades in at [slot]. If it is moving, [from] is the slot it is leaving. */
    data class Enter<K>(val key: K, val slot: Int, val from: Int?) : RowStep<K>
}

/**
 * Works out how the panel goes from the rows [shown], by key and slot, to the [next] list, given
 * the rows still [leaving].
 *
 * A row only stays if it keeps its slot. A row that moves fades out where it was and fades in where
 * it goes: sliding instead, rows swapping places pass through each other, which the video never
 * shows.
 */
fun <K> planRows(
    shown: List<Pair<K, Int>>,
    leaving: List<Pair<K, Int>>,
    next: List<K>,
): List<RowStep<K>> {
    val wanted = next.withIndex().associate { (slot, key) -> key to slot }
    val steps = mutableListOf<RowStep<K>>()
    for ((key, slot) in shown) {
        steps += if (wanted[key] == slot) RowStep.Keep(key, slot) else RowStep.Leave(key, slot)
    }
    val shownSlots = shown.toMap()
    val leavingRows = leaving.toSet()
    for ((slot, key) in next.withIndex()) {
        when {
            shownSlots[key] == slot -> Unit
            (key to slot) in leavingRows -> steps += RowStep.Revive(key, slot)
            else -> steps += RowStep.Enter(key, slot, from = shownSlots[key])
        }
    }
    return steps
}
