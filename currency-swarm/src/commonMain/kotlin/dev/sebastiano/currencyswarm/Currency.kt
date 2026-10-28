package dev.sebastiano.currencyswarm

/** A currency the swarm can spell: its sign, its name and its ISO code. */
data class Currency(val symbol: String, val name: String, val code: String)

/** The signs of the original, in its order. */
val Currencies: List<Currency> =
    listOf(
        Currency("₹", "Indian rupee", "INR"),
        Currency("$", "US dollar", "USD"),
        Currency("₩", "South Korean won", "KRW"),
        Currency("¥", "Japanese yen", "JPY"),
    )
