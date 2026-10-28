package dev.sebastiano.expandingsearch

/** One thing to do to a row of the panel, named by its [key] and the [slot] it is in or goes to. */
sealed interface RowStep<K> {
    /** The row stays, sliding to [slot] if that is a new one; its text may change. */
    data class Keep<K>(val key: K, val slot: Int) : RowStep<K>

    /** A row still fading out is wanted again, so it comes back, at [slot]. */
    data class Revive<K>(val key: K, val slot: Int) : RowStep<K>

    /** The row fades out where it is. */
    data class Leave<K>(val key: K, val slot: Int) : RowStep<K>

    /** A new row fades in at [slot]. */
    data class Enter<K>(val key: K, val slot: Int) : RowStep<K>
}

/**
 * Works out how the panel goes from the rows [shown], by key and slot, to the [next] list, given
 * the rows still [leaving].
 *
 * A row in both lists stays and slides to its new slot, through any rows in the way, as in the
 * video. A row still fading out that is wanted again comes back rather than being doubled.
 */
fun <K> planRows(
    shown: List<Pair<K, Int>>,
    leaving: List<Pair<K, Int>>,
    next: List<K>,
): List<RowStep<K>> {
    val wanted = next.withIndex().associate { (slot, key) -> key to slot }
    val steps = mutableListOf<RowStep<K>>()
    for ((key, slot) in shown) {
        steps += wanted[key]?.let { RowStep.Keep(key, it) } ?: RowStep.Leave(key, slot)
    }
    val shownKeys = shown.map { it.first }.toSet()
    val leavingKeys = leaving.map { it.first }.toSet()
    for ((slot, key) in next.withIndex()) {
        when (key) {
            in shownKeys -> Unit
            in leavingKeys -> steps += RowStep.Revive(key, slot)
            else -> steps += RowStep.Enter(key, slot)
        }
    }
    return steps
}
