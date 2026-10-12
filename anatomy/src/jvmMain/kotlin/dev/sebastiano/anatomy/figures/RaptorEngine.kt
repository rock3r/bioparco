// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import dev.sebastiano.anatomy.kit.audit.AuditOptions
import dev.sebastiano.anatomy.kit.audit.AuditedFigure
import dev.sebastiano.anatomy.kit.audit.Recorder

/**
 * Fig 6, the Raptor 2 on its test stand, built and settled once: the original's build.mjs up to
 * its page. The audit excuses what the original's does: two plumbing lines that cross the
 * harness's bundle on purpose.
 */
internal object RaptorEngine : AuditedFigure {
    val BUILD: RaptorBuild by lazy { RaptorBuild().also { it.recorder.settle() } }

    override fun record(): Recorder = BUILD.recorder

    override val auditOptions: AuditOptions
        get() =
            AuditOptions(
                crowd =
                    listOf(
                        "plumbing.lox-to-frpb" to "controller.cable-block",
                        "plumbing.lox-to-frpb" to "controller.harness-frpb",
                    )
            )
}
