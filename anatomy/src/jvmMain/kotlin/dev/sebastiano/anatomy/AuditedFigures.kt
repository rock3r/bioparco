package dev.sebastiano.anatomy

import dev.sebastiano.anatomy.figures.TurningDial
import dev.sebastiano.anatomy.kit.audit.AuditedFigure
import dev.sebastiano.anatomy.kit.audit.auditReport
import dev.sebastiano.anatomy.kit.turn.TurningFigure
import dev.sebastiano.anatomy.kit.turn.orbitReport
import dev.sebastiano.anatomy.kit.turn.orbitVerdict
import java.io.File

/*
 * The figures `capture --audit` can audit: a figure whose object implements `AuditedFigure`
 * (it records its items, solids and routes into a `Recorder`) is listed here by its `Figure`.
 * Figures that aren't listed draw without recording, and `--audit` says so.
 */

/** Each audited figure's recorder, by figure. Add a figure's object here once it records. */
internal val AUDITED_FIGURES: Map<Figure, AuditedFigure> =
    mapOf(
        Figure.EspressoMachine to dev.sebastiano.anatomy.figures.EspressoMachine,
        Figure.TurningDial to TurningDial,
    )

/**
 * Records [figure], runs the audit, prints the report, writes it to [log], and returns how many
 * problems fail it. This is the original's `auditOrExit`, without the exit. A [TurningFigure] then
 * runs the orbit audit too (`orbitOrExit`), [quick] if asked, with progress if [verbose].
 */
internal fun auditFigure(
    figure: AuditedFigure,
    log: File,
    quick: Boolean = false,
    verbose: Boolean = false,
): Int {
    val recorder = figure.record()
    val run = auditReport(recorder, options = figure.auditOptions)
    print(run.text)
    var text = run.text
    var failures = run.failures
    if (figure is TurningFigure) {
        val say = if (verbose) { line: String -> println("  … $line") } else null
        val result = figure.orbitAudit(quick, say)
        val orbit = "${orbitReport(result)}\n${orbitVerdict(result)}\n"
        print(orbit)
        text += orbit
        failures += result.failures
    }
    log.absoluteFile.parentFile.mkdirs()
    log.writeText(text)
    println("wrote ${log.path}")
    return failures
}
