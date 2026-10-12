package dev.sebastiano.anatomy.kit.turn

import dev.sebastiano.anatomy.kit.audit.AuditedFigure

/**
 * A turning figure that the audit can prove: `capture <figure> --audit` runs the kit audit on its
 * recorder and then the orbit audit over its turn and clips, as the original's examples call
 * `auditOrExit` and then `orbitOrExit`.
 */
interface TurningFigure : AuditedFigure {
    /**
     * Runs the orbit audit on the built figure with its sweep, clips and exemptions. [quick]
     * samples about ten times fewer states, for iteration; [log] hears each check as it starts.
     */
    fun orbitAudit(quick: Boolean, log: ((String) -> Unit)?): OrbitResult
}
