// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.figures

import dev.sebastiano.anatomy.kit.Art
import dev.sebastiano.anatomy.kit.ArtBuilder
import dev.sebastiano.anatomy.kit.IsoPath
import dev.sebastiano.anatomy.kit.Knot
import dev.sebastiano.anatomy.kit.Plan
import dev.sebastiano.anatomy.kit.Point3
import dev.sebastiano.anatomy.kit.audit.AuditShape
import dev.sebastiano.anatomy.kit.audit.PartRecorder
import dev.sebastiano.anatomy.kit.audit.PipeRoute
import dev.sebastiano.anatomy.kit.audit.Recorder
import dev.sebastiano.anatomy.kit.audit.TubeShape
import dev.sebastiano.anatomy.kit.audit.ValvePort

/**
 * One run of the original's build.mjs: the stand behind the engine ([backUnder], the deck glow's
 * [deckTop], the cone's [shadow], [backOver]), every engine part recorded into [recorder] and put
 * as an item, and the nozzle [lip] the rim glow strokes. The parts the build lands later parts on
 * are kept here between the sections that draw them.
 */
internal class RaptorBuild {
    val pieces = Raptor.pieces()
    val recorder = Recorder(Raptor.P, Raptor.STAGGER, Raptor.SPRING)
    val parts = PartRecorder(recorder, pieces)

    /** The back art up to the deck glow. */
    val under = ArtBuilder()
    /** The back art after the cone's shadow. */
    val over = ArtBuilder()
    var deckTop: IsoPath = IsoPath.Empty
    var shadow: IsoPath = IsoPath.Empty
    var lip: IsoPath = IsoPath.Empty

    val backUnder: Art
        get() = under.build()

    val backOver: Art
        get() = over.build()

    // The nozzle's cooling manifold.
    var manifoldR = 40.0

    // The chamber.
    lateinit var jackets: List<AuditShape>
    lateinit var rings: List<AuditShape>
    lateinit var head: AuditShape
    lateinit var injector: AuditShape
    lateinit var dome: AuditShape
    val jacketBoss = HashMap<Int, Point3>()
    val domeBoss = HashMap<Int, Pair<Point3, Point3>>()

    // The oxygen-rich preburner.
    lateinit var orpbBody: AuditShape
    lateinit var orpbPlug: Point3
    lateinit var orpbExciter: Point3
    lateinit var orpbSense: Point3

    // The oxygen turbopump.
    lateinit var otpRing: AuditShape
    lateinit var voluteTan: Point3
    lateinit var loxStart: Point3
    lateinit var otpSense: Point3
    lateinit var purgeC: Point3
    lateinit var pressLox: Point3
    lateinit var purgeF: Point3

    // The methane turbopump and fuel-rich preburner.
    lateinit var ftpLow: AuditShape
    lateinit var ftpRing: AuditShape
    lateinit var ftpTurbine: AuditShape
    lateinit var ftpNeck: AuditShape
    lateinit var ftpHousing: AuditShape
    lateinit var fvTan: Point3
    lateinit var fuelStart: Point3
    lateinit var frpbPlug: Point3
    lateinit var frpbExciterAt: Raptor.OnBody
    lateinit var frpbExciter: Point3
    lateinit var ftpSense: Point3
    lateinit var ftpPumpSense: Point3
    lateinit var ftpPickup: Point3

    // The ducts.
    lateinit var downElbow: Point3
    lateinit var mfv: ValvePort
    lateinit var lox: PipeRoute
    lateinit var loxRun: List<TubeShape>
    lateinit var mov: ValvePort

    // The plumbing block the sense lines run into.
    val lines = ArrayList<List<Point3>>()
    lateinit var block: Plan
    var blockZ = 0.0
    var senses = 0

    init {
        stand()
        nozzle()
        chamber()
        preburner()
        oxygenPump()
        gimbal()
        methanePump()
        struts()
        ducts()
        feedLines()
        senseLines()
        controller()
        standParts()
        actuators()
        beam()
        pieces.getValue("plumbing").say =
            "plumbing · $senses sense lines into a block · ${lines.size - senses} feed, purge and igniter lines"
        pieces.getValue("controller").say =
            "engine controller · ${parts.cables.count { it.first == "controller" }} cables"
    }

    companion object {
        val DOME_PROFILE =
            listOf(Knot(210.0, 34.4), Knot(214.0, 34.0, true), Knot(218.0, 32.0, true), Knot(222.0, 27.5))
        val FTP_LOW_PROFILE =
            listOf(
                Knot(166.0, 3.5),
                Knot(168.0, 8.0, true),
                Knot(171.0, 10.6, true),
                Knot(175.0, 11.6),
                Knot(194.0, 11.6),
            )
    }
}
