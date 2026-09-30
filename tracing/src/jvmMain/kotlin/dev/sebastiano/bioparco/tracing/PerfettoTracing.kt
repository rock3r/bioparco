package dev.sebastiano.bioparco.tracing

import androidx.tracing.DelicateTracingApi
import androidx.tracing.Tracer
import androidx.tracing.wire.TraceDriver
import androidx.tracing.wire.TraceSink
import java.io.File

/** The system property that turns tracing on, naming the directory for the trace files. */
const val TRACE_DIR_PROPERTY = "bioparco.trace.dir"

/** The environment variable that turns tracing on, if the system property is not set. */
const val TRACE_DIR_ENVIRONMENT = "BIOPARCO_TRACE_DIR"

/**
 * Starts writing [Tracing] sections to a Perfetto trace in [directory], and returns what stops it
 * and flushes the file. Open the file at https://ui.perfetto.dev.
 */
fun startTracing(directory: File): AutoCloseable {
    directory.mkdirs()
    val driver = TraceDriver(TraceSink(directory))
    Tracing.install(PerfettoSink(driver.tracer))
    return AutoCloseable {
        Tracing.install(null)
        driver.close()
    }
}

/**
 * Starts tracing into the [name] folder of the directory [TRACE_DIR_PROPERTY] or
 * [TRACE_DIR_ENVIRONMENT] names, if either does, and stops it when the JVM exits. Every specimen's
 * `main` calls this first, with the specimen's module name; it does nothing otherwise.
 */
fun startTracingFromEnvironment(name: String) {
    if (Tracing.isEnabled) return
    val directory =
        traceDirectory(System.getProperty(TRACE_DIR_PROPERTY), System.getenv(TRACE_DIR_ENVIRONMENT))
            ?: return
    val tracing = startTracing(File(directory, name))
    Runtime.getRuntime().addShutdownHook(Thread({ tracing.close() }, "bioparco-trace-flush"))
}

@OptIn(DelicateTracingApi::class)
private class PerfettoSink(private val tracer: Tracer) : Tracing.Sink {
    override fun begin(name: String): Any =
        tracer.beginSection(CATEGORY, name, tracer.tokenFromThreadContext(), metadataBlock = {})

    override fun end(section: Any?) {
        (section as AutoCloseable).close()
    }

    private companion object {
        const val CATEGORY = "bioparco"
    }
}
