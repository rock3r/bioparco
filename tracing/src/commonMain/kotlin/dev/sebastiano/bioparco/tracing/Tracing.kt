package dev.sebastiano.bioparco.tracing

import kotlin.concurrent.Volatile

/**
 * Trace sections for any specimen. Off by default: a section is then one field read and a branch,
 * cheap enough to leave in any hot path. The JVM side installs a Perfetto sink when a trace
 * directory is set; see `docs/TRACING.md`.
 */
object Tracing {
    @PublishedApi @Volatile internal var sink: Sink? = null

    val isEnabled: Boolean
        get() = sink != null

    /** Sends sections to [sink] from now on, or nowhere with `null`. */
    fun install(sink: Sink?) {
        this.sink = sink
    }

    /** Runs [block] inside a section called [name], when tracing is on. */
    inline fun <T> section(name: String, block: () -> T): T {
        val current = sink ?: return block()
        val section = current.begin(name)
        try {
            return block()
        } finally {
            current.end(section)
        }
    }

    /** Where sections go. [end] gets back whatever [begin] returned, on the same thread. */
    interface Sink {
        fun begin(name: String): Any?

        fun end(section: Any?)
    }
}

/** The trace directory: the `bioparco.trace.dir` [property], else the environment's, if set. */
fun traceDirectory(property: String?, environment: String?): String? =
    property?.takeIf { it.isNotBlank() } ?: environment?.takeIf { it.isNotBlank() }
