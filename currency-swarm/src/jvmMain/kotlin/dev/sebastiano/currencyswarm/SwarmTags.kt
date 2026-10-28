package dev.sebastiano.currencyswarm

/** Test tags for driving the specimen from Spectre. */
object SwarmTags {
    /** The dots. A click here moves to the next sign. */
    const val STAGE = "currency-swarm-stage"

    /** Its semantics text is the name of the currency the dots are spelling. */
    const val NAME = "currency-swarm-name"

    /** The chip that picks [code]'s sign. */
    fun chip(code: String): String = "currency-swarm-chip-$code"
}
