package dev.sebastiano.anatomy

import dev.sebastiano.anatomy.kit.audit.AuditedFigure
import dev.sebastiano.anatomy.kit.audit.auditReport
import java.io.File

/*
 * The figures `capture --audit` can audit: a figure whose object implements `AuditedFigure`
 * (it records its items, solids and routes into a `Recorder`) is listed here by its `Figure`.
 * Figures that aren't listed draw without recording, and `--audit` says so.
 */

/** Each audited figure's recorder, by figure. Add a figure's object here once it records. */
internal val AUDITED_FIGURES: Map<Figure, AuditedFigure> =
    mapOf(Figure.EspressoMachine to dev.sebastiano.anatomy.figures.EspressoMachine)

/**
 * Records [figure], runs the audit, prints the report, writes it to [log], and returns how many
 * problems fail it. This is the original's `auditOrExit`, without the exit.
 */
internal fun auditFigure(figure: AuditedFigure, log: File): Int {
    val recorder = figure.record()
    val run = auditReport(recorder, options = figure.auditOptions)
    print(run.text)
    log.absoluteFile.parentFile.mkdirs()
    log.writeText(run.text)
    println("wrote ${log.path}")
    return run.failures
}
