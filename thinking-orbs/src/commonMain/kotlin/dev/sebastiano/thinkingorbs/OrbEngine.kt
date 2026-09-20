package dev.sebastiano.thinkingorbs

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Formula-for-formula port of the upstream ThinkingOrbs engine. */
internal object OrbEngine {
    fun frame(mode: OrbMode, size: Double, time: Double, options: Map<String, Double>): OrbFrame =
        when (mode) {
            OrbMode.Orbits -> orbits(size, time, options)
            OrbMode.Globe -> globe(size, time, options)
            OrbMode.Rubik -> rubik(size, time, options)
            OrbMode.Wave -> wave(size, time, options)
            OrbMode.Web -> web(size, time, options)
            OrbMode.Braid -> braid(size, time, options)
            OrbMode.Ribbon,
            OrbMode.Ring -> ribbon(size, time, options)
            OrbMode.Morph -> morph(size, time, options)
        }

    fun jsRound(value: Double): Double = floor(value + 0.5)
}

private fun lerp(start: Double, end: Double, fraction: Double): Double =
    start + (end - start) * fraction

private fun hash(first: Double, second: Double): Double {
    val value = sin(first * 12.9898 + second * 78.233) * 43758.5453
    return value - floor(value)
}

private fun noise(x: Double, y: Double): Double {
    val xi = floor(x)
    val yi = floor(y)
    var fx = x - xi
    var fy = y - yi
    fx = fx * fx * (3 - 2 * fx)
    fy = fy * fy * (3 - 2 * fy)
    val a = hash(xi, yi)
    val b = hash(xi + 1, yi)
    val c = hash(xi, yi + 1)
    val d = hash(xi + 1, yi + 1)
    return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy
}

private data class Point3(val x: Double, val y: Double, val z: Double)

private fun fibonacciDirection(index: Int, count: Double): Point3 {
    val golden = PI * (3 - sqrt(5.0))
    val y = 1 - (2 * (index + 0.5)) / count
    val radius = sqrt(1 - y * y)
    val angle = index * golden
    return Point3(radius * cos(angle), y, radius * sin(angle))
}

private fun radiusScale(size: Double, power: Double): Double = (size / 300).pow(power)

private class Projector(
    yaw: Double,
    tilt: Double,
    private val centerX: Double,
    private val centerY: Double,
    private val scale: Double,
) {
    private val sinTilt = sin(tilt)
    private val cosTilt = cos(tilt)
    private val sinYaw = sin(yaw)
    private val cosYaw = cos(yaw)

    operator fun invoke(x: Double, y: Double, z: Double): Point3 {
        val x1 = x * cosYaw + z * sinYaw
        val z1 = -x * sinYaw + z * cosYaw
        val y1 = y * cosTilt - z1 * sinTilt
        val z2 = y * sinTilt + z1 * cosTilt
        return Point3(centerX + x1 * scale, centerY - y1 * scale, z2)
    }
}

private fun finalize(dots: List<OrbDot>, lines: List<OrbLine>, minimumRadius: Double): OrbFrame {
    val visible =
        dots
            .withIndex()
            .filter { it.value.alpha >= 0.02 }
            .map { indexed ->
                indexed.index to
                    indexed.value.copy(radius = max(minimumRadius, indexed.value.radius))
            }
            .sortedWith(compareBy<Pair<Int, OrbDot>> { it.second.z }.thenBy { it.first })
            .map { it.second }
    return OrbFrame(visible, lines.filter { it.alpha >= 0.02 })
}

private fun count(options: Map<String, Double>, key: String, fallback: Double) =
    options[key] ?: fallback

private fun below(value: Double): Int = if (value > 0) ceil(value).toInt() else 0

private fun orbits(size: Double, time: Double, options: Map<String, Double>): OrbFrame {
    val center = size / 2
    val radius = center * 0.82
    val projector = Projector(time * 0.12, 0.3, center, center, 1.0)
    val radiusScale = radiusScale(size, options["rsPow"] ?: 0.6)
    val orbitCount = count(options, "orbitN", 12.0)
    val ghostCount = count(options, "ghostN", 40.0)
    val particleCount = count(options, "particles", 3.0)
    val dots = mutableListOf<OrbDot>()

    repeat(below(orbitCount)) { orbit ->
        val h1 = hash(orbit.toDouble(), 1.7)
        val h2 = hash(orbit.toDouble(), 5.2)
        val h3 = hash(orbit.toDouble(), 8.9)
        val orbitRadius = radius * (0.45 + 0.52 * h1)
        val theta = h1 * 2 * PI
        val phi = acos(2 * h2 - 1)
        val nx = sin(phi) * cos(theta)
        val ny = cos(phi)
        val nz = sin(phi) * sin(theta)
        var ux = -ny
        var uy = nx
        val uz = 0.0
        val length = max(1e-6, sqrt(ux * ux + uy * uy))
        ux /= length
        uy /= length
        val vx = ny * uz - nz * uy
        val vy = nz * ux - nx * uz
        val vz = nx * uy - ny * ux
        val speed = (0.25 + 0.55 * h3) * if (h3 > 0.5) 1 else -1

        repeat(below(ghostCount)) { index ->
            val angle = (index / ghostCount) * 2 * PI
            val point =
                projector(
                    (ux * cos(angle) + vx * sin(angle)) * orbitRadius,
                    (uy * cos(angle) + vy * sin(angle)) * orbitRadius,
                    (uz * cos(angle) + vz * sin(angle)) * orbitRadius,
                )
            val depth = (point.z / orbitRadius + 1) / 2
            dots +=
                OrbDot(
                    point.x,
                    point.y,
                    point.z,
                    (options["ghostR"] ?: 0.9) * radiusScale,
                    0.72,
                    (options["ghostA"] ?: 0.5) * (0.4 + 0.6 * depth),
                )
        }
        repeat(below(particleCount)) { particle ->
            val angle = time * speed + (particle / particleCount) * 2 * PI + h2 * 6
            val point =
                projector(
                    (ux * cos(angle) + vx * sin(angle)) * orbitRadius,
                    (uy * cos(angle) + vy * sin(angle)) * orbitRadius,
                    (uz * cos(angle) + vz * sin(angle)) * orbitRadius,
                )
            val depth = (point.z / orbitRadius + 1) / 2
            dots +=
                OrbDot(
                    point.x,
                    point.y,
                    point.z,
                    ((options["partR"] ?: 1.2) + (options["partRDepth"] ?: 1.6) * depth) *
                        radiusScale,
                    0.3 - 0.22 * depth,
                )
        }
    }
    return finalize(dots, emptyList(), options["rMin"] ?: 0.3)
}

private fun globe(size: Double, time: Double, options: Map<String, Double>): OrbFrame {
    val spin = 0.5
    val center = size / 2
    val radius = center * 0.82
    val tilt = 0.4 + 0.06 * sin(time * 0.35)
    val projector = Projector(time * spin, tilt, center, center, radius)
    val scan = time * (spin + (1.7 - spin) * (options["scanMul"] ?: 1.0))
    val radiusScale = radiusScale(size, options["rsPow"] ?: 0.6)
    val rings = count(options, "latRings", 17.0)
    val density = options["lonDensity"] ?: 44.0
    val dots = mutableListOf<OrbDot>()
    repeat(floor(rings).toInt() + 1) { ring ->
        val latitude = -PI / 2 + (ring / rings) * PI
        val cosLatitude = cos(latitude)
        val sinLatitude = sin(latitude)
        val longitudeCount = max(1, OrbEngine.jsRound(abs(cosLatitude) * density).toInt())
        repeat(longitudeCount) { longitudeIndex ->
            val longitude = longitudeIndex.toDouble() / longitudeCount * 2 * PI
            val point =
                projector(cosLatitude * cos(longitude), sinLatitude, cosLatitude * sin(longitude))
            val depth = (point.z + 1) / 2
            val delta =
                atan2(sin(longitude + time * spin - scan), cos(longitude + time * spin - scan))
            val boost = exp(-(delta * delta) / 0.18) * max(0.0, point.z)
            val dimBase = options["dimBase"] ?: 1.0
            dots +=
                OrbDot(
                    point.x,
                    point.y,
                    point.z,
                    ((options["rBase"] ?: 0.6) +
                        (options["rDepth"] ?: 1.7) * depth +
                        (options["rBoost"] ?: 1.0) * boost) * radiusScale,
                    (options["inkFar"] ?: 0.62) - (options["inkSpan"] ?: 0.54) * depth,
                    dimBase + (1 - dimBase) * min(1.0, boost),
                )
        }
    }
    return finalize(dots, emptyList(), options["rMin"] ?: 0.3)
}

private data class Move(val axis: Int, val low: Double, val high: Double, val angle: Double)

private data class SolveCycle(val amounts: DoubleArray, val active: Int)

private fun solveCycle(time: Double, count: Int, slotDuration: Double, rest: Double): SolveCycle {
    val cycle = 2 * count * slotDuration + rest
    var cycleTime = time % cycle
    if (cycleTime < 0) cycleTime += cycle
    val amounts = DoubleArray(count)
    var active = -1
    if (cycleTime < 2 * count * slotDuration) {
        val slot = floor(cycleTime / slotDuration).toInt()
        val progress = (cycleTime - slot * slotDuration) / slotDuration
        val eased = 1 - (1 - min(1.0, progress / 0.7)).pow(3)
        if (slot < count) {
            repeat(slot) { amounts[it] = 1.0 }
            amounts[slot] = eased
            active = slot
        } else {
            val reverse = 2 * count - 1 - slot
            repeat(reverse) { amounts[it] = 1.0 }
            amounts[reverse] = 1 - eased
            active = reverse
        }
    }
    return SolveCycle(amounts, active)
}

private fun moves(count: Int): List<Move> =
    List(count) { index ->
        val value = index.toDouble()
        val axis = min(2, floor(hash(value, 2.3) * 3).toInt())
        val low = -1.0 + 0.5 * min(3.0, floor(hash(value, 5.9) * 4))
        val direction = if (hash(value, 7.7) < 0.5) 1 else -1
        Move(axis, low, low + 0.5, direction * PI / 2)
    }

private data class MovedPoint(val point: Point3, val active: Boolean)

private fun applyMoves(
    source: Point3,
    moves: List<Move>,
    amounts: DoubleArray,
    active: Int,
): MovedPoint {
    var (x, y, z) = source
    var inActive = false
    for (index in moves.indices) {
        if (amounts[index] > 0) {
            val move = moves[index]
            val coordinate =
                when (move.axis) {
                    0 -> x
                    1 -> y
                    else -> z
                }
            if (coordinate >= move.low && coordinate < move.high) {
                if (index == active) inActive = true
                val angle = move.angle * amounts[index]
                val cosine = cos(angle)
                val sine = sin(angle)
                when (move.axis) {
                    0 -> {
                        val nextY = y * cosine - z * sine
                        z = y * sine + z * cosine
                        y = nextY
                    }
                    1 -> {
                        val nextX = x * cosine + z * sine
                        z = -x * sine + z * cosine
                        x = nextX
                    }
                    else -> {
                        val nextX = x * cosine - y * sine
                        y = x * sine + y * cosine
                        x = nextX
                    }
                }
            }
        }
    }
    return MovedPoint(Point3(x, y, z), inActive)
}

private fun rubik(size: Double, time: Double, options: Map<String, Double>): OrbFrame {
    val center = size / 2
    val radius = center * 0.82
    val projector = Projector(time * 0.55, 0.35 + 0.1 * sin(time * 0.9), center, center, radius)
    val radiusScale = radiusScale(size, options["rsPow"] ?: 0.6)
    val moveCount = count(options, "moveCount", 14.0).toInt()
    val moves = moves(moveCount)
    val cycle = solveCycle(time, moveCount, 0.42, 1.2)
    val rings = count(options, "latRings", 15.0)
    val density = options["lonDensity"] ?: 40.0
    val dots = mutableListOf<OrbDot>()
    repeat(floor(rings).toInt() + 1) { ring ->
        val latitude = -PI / 2 + (ring / rings) * PI
        val cosLatitude = cos(latitude)
        val sinLatitude = sin(latitude)
        val longitudeCount = max(1, OrbEngine.jsRound(abs(cosLatitude) * density).toInt())
        repeat(longitudeCount) { longitudeIndex ->
            val longitude = longitudeIndex.toDouble() / longitudeCount * 2 * PI
            val moved =
                applyMoves(
                    Point3(cosLatitude * cos(longitude), sinLatitude, cosLatitude * sin(longitude)),
                    moves,
                    cycle.amounts,
                    cycle.active,
                )
            val point = projector(moved.point.x, moved.point.y, moved.point.z)
            val depth = (point.z + 1) / 2
            dots +=
                OrbDot(
                    point.x,
                    point.y,
                    point.z,
                    ((options["rBase"] ?: 0.6) +
                        (options["rDepth"] ?: 1.7) * depth +
                        if (moved.active) options["rActive"] ?: 0.3 else 0.0) * radiusScale,
                    (options["inkFar"] ?: 0.62) -
                        (options["inkSpan"] ?: 0.54) * depth -
                        if (moved.active) 0.14 else 0.0,
                )
        }
    }
    return finalize(dots, emptyList(), options["rMin"] ?: 0.3)
}

private fun wave(size: Double, time: Double, options: Map<String, Double>): OrbFrame {
    val center = size / 2
    val radius = center * 0.874
    val projector = Projector(time * 0.18, 0.38, center, center, 1.0)
    val radiusScale = radiusScale(size, options["rsPow"] ?: 0.6)
    val rings = count(options, "rings", 15.0)
    val density = options["lonDensity"] ?: 40.0
    val dots = mutableListOf<OrbDot>()
    repeat(floor(rings).toInt() + 1) { ring ->
        val latitude = -PI / 2 + (ring / rings) * PI
        val cosLatitude = cos(latitude)
        val sinLatitude = sin(latitude)
        val wobble = 0.62 * sin(time * 2.1 - ring * 0.52) + 0.38 * sin(time * 1.27 + ring * 0.83)
        val ringRadius = radius * (0.88 + 0.105 * wobble)
        val longitudeCount = max(1, OrbEngine.jsRound(abs(cosLatitude) * density).toInt())
        repeat(longitudeCount) { longitudeIndex ->
            val longitude = longitudeIndex.toDouble() / longitudeCount * 2 * PI
            val point =
                projector(
                    cosLatitude * cos(longitude) * ringRadius,
                    sinLatitude * ringRadius,
                    cosLatitude * sin(longitude) * ringRadius,
                )
            val depth = (point.z / radius + 1) / 2
            val crest = max(0.0, wobble)
            dots +=
                OrbDot(
                    point.x,
                    point.y,
                    point.z,
                    ((options["rBase"] ?: 0.6) + (options["rDepth"] ?: 1.7) * depth) *
                        (1 + 0.4 * crest) *
                        radiusScale,
                    0.66 - 0.56 * depth - 0.1 * crest,
                )
        }
    }
    return finalize(dots, emptyList(), options["rMin"] ?: 0.3)
}

private fun web(size: Double, time: Double, options: Map<String, Double>): OrbFrame {
    val center = size / 2
    val radius = center * 0.8 * (options["spread"] ?: 1.0)
    val projector = Projector(time * 0.12, 0.32, center, center, radius)
    val radiusScale = radiusScale(size, options["rsPow"] ?: 0.6)
    val nodeCount = count(options, "nodeN", 30.0)
    val threshold = options["thr"] ?: 0.72
    val nodeRadius = options["nodeR"] ?: 1.4
    val nodeDepthRadius = options["nodeRDepth"] ?: 1.8
    val nodes =
        List(below(nodeCount)) { index ->
            val direction = fibonacciDirection(index, nodeCount)
            val x = direction.x + 0.6 * (noise(index * 0.31 + 9, time * 0.24) - 0.5)
            val y = direction.y + 0.6 * (noise(index * 0.53 + 27, time * 0.21) - 0.5)
            val z = direction.z + 0.6 * (noise(index * 0.77 + 55, time * 0.27) - 0.5)
            val length = sqrt(x * x + y * y + z * z)
            Point3(x / length, y / length, z / length)
        }
    val lines = mutableListOf<OrbLine>()
    val dots = mutableListOf<OrbDot>()
    for (first in nodes.indices) {
        for (second in first + 1 until nodes.size) {
            val dx = nodes[first].x - nodes[second].x
            val dy = nodes[first].y - nodes[second].y
            val dz = nodes[first].z - nodes[second].z
            val distance = sqrt(dx * dx + dy * dy + dz * dz)
            if (distance >= threshold) continue
            val start = projector(nodes[first].x, nodes[first].y, nodes[first].z)
            val end = projector(nodes[second].x, nodes[second].y, nodes[second].z)
            val depth = ((start.z + end.z) / 2 + 1) / 2
            lines +=
                OrbLine(
                    start.x,
                    start.y,
                    end.x,
                    end.y,
                    0.42,
                    (1 - distance / threshold) * (0.3 + 0.55 * depth),
                    max(0.6, (options["lineW"] ?: 0.8) * radiusScale),
                )
        }
    }
    nodes.forEachIndexed { index, node ->
        val point = projector(node.x, node.y, node.z)
        val depth = (point.z + 1) / 2
        val pulse = 1 + 0.25 * sin(time * 1.4 + index * 2.7)
        dots +=
            OrbDot(
                point.x,
                point.y,
                point.z,
                (nodeRadius + nodeDepthRadius * depth) * pulse * radiusScale,
                0.55 - 0.45 * depth,
            )
    }
    repeat(below(count(options, "signals", 5.0))) { signal ->
        val segment = floor(time * 0.55 + signal * 7.31)
        val first = floor(hash(segment, signal * 3.1 + 1.7) * nodeCount).toInt()
        val second = floor(hash(segment, signal * 5.7 + 4.2) * nodeCount).toInt()
        if (first == second) return@repeat
        val signalTime = time * 0.55 + signal * 7.31
        val fraction = signalTime - floor(signalTime)
        val x = lerp(nodes[first].x, nodes[second].x, fraction)
        val y = lerp(nodes[first].y, nodes[second].y, fraction)
        val z = lerp(nodes[first].z, nodes[second].z, fraction)
        val length = max(1e-6, sqrt(x * x + y * y + z * z))
        val point = projector(x / length, y / length, z / length)
        val depth = (point.z + 1) / 2
        dots +=
            OrbDot(
                point.x,
                point.y,
                point.z,
                (nodeRadius * 1.5 + nodeDepthRadius * depth) * radiusScale,
                0.05,
                0.5 + 0.5 * depth,
            )
    }
    return finalize(dots, lines, options["rMin"] ?: 0.3)
}

private fun braid(size: Double, time: Double, options: Map<String, Double>): OrbFrame {
    val center = size / 2
    val radius = center * 0.76
    val projector = Projector(time * 0.4, 0.3, center, center, 1.0)
    val radiusScale = radiusScale(size, options["rsPow"] ?: 0.6)
    val dots = mutableListOf<OrbDot>()
    val ghostCount = count(options, "ghostN", 150.0)
    repeat(below(ghostCount)) { index ->
        val direction = fibonacciDirection(index, ghostCount)
        val point = projector(direction.x * radius, direction.y * radius, direction.z * radius)
        val depth = (point.z / radius + 1) / 2
        dots += OrbDot(point.x, point.y, point.z, 0.8 * radiusScale, 0.78, 0.1 + 0.22 * depth)
    }
    val strandCount = count(options, "strandN", 52.0)
    repeat(3) { strand ->
        val phase = strand / 3.0 * 2 * PI
        repeat(below(strandCount)) { index ->
            val strandTime = index / strandCount + time * 0.045
            val vertical = ((strandTime - floor(strandTime)) * 2 - 1) * 0.96
            val surface = sqrt(max(0.0, 1 - vertical * vertical))
            val endFade = min(1.0, (1 - abs(vertical)) / 0.1)
            val angle = vertical * PI * (options["turns"] ?: 3.0) + phase
            val weave =
                1 +
                    0.075 *
                        sin(vertical * PI * (options["turns"] ?: 3.0) * 2 + phase * 2 + time * 0.8)
            val ringRadius = surface * radius * weave
            val point =
                projector(
                    cos(angle) * ringRadius,
                    vertical * radius * weave,
                    sin(angle) * ringRadius,
                )
            val depth = (point.z / radius + 1) / 2
            dots +=
                OrbDot(
                    point.x,
                    point.y,
                    point.z,
                    ((options["rBase"] ?: 1.2) + (options["rDepth"] ?: 1.8) * depth) * radiusScale,
                    0.55 - 0.45 * depth,
                    endFade * (0.45 + 0.55 * depth),
                )
        }
    }
    return finalize(dots, emptyList(), options["rMin"] ?: 0.3)
}

private fun ribbon(size: Double, time: Double, options: Map<String, Double>): OrbFrame {
    val center = size / 2
    val radius = center * 0.78
    val spin = options["spin"] ?: 1.0
    val cameraTilt = 0.3
    val projector = Projector(time * 0.1 * spin, cameraTilt, center, center, 1.0)
    val radiusScale = radiusScale(size, options["rsPow"] ?: 0.6)
    val faceOn = (options["faceOn"] ?: 0.0) != 0.0
    val wobbleMultiplier = options["wobMul"] ?: 1.0
    val dots = mutableListOf<OrbDot>()
    val ghostCount = count(options, "ghostN", 150.0)
    repeat(below(ghostCount)) { index ->
        val direction = fibonacciDirection(index, ghostCount)
        val point = projector(direction.x * radius, direction.y * radius, direction.z * radius)
        val depth = (point.z / radius + 1) / 2
        dots += OrbDot(point.x, point.y, point.z, 0.8 * radiusScale, 0.78, 0.1 + 0.22 * depth)
    }
    val yaw = time * 0.24 * spin
    val tilt = if (faceOn) -cameraTilt else 0.55 + 0.3 * sin(time * 0.18) * spin
    val ux = cos(yaw)
    val uy = 0.0
    val uz = sin(yaw)
    val vx = -uz * sin(tilt)
    val vy = cos(tilt)
    val vz = ux * sin(tilt)
    val nx = uy * vz - uz * vy
    val ny = uz * vx - ux * vz
    val nz = ux * vy - uy * vx
    val wobbleAmplitude = 0.23 * wobbleMultiplier
    val baseRadius = if (faceOn) radius / (1 + 0.85 * wobbleAmplitude) else radius
    val segments = count(options, "segs", 88.0)
    val lanes =
        max(1, OrbEngine.jsRound((options["lanes"] ?: 5.0) * (options["bandMul"] ?: 1.0)).toInt())
    val middle = (lanes - 1) / 2.0
    repeat(lanes) { lane ->
        val laneOffset = (lane - middle) * 0.075
        val edge = abs(lane - middle) / max(1.0, middle)
        repeat(below(segments)) { segment ->
            val angle = segment / segments * 2 * PI
            val wobble =
                (0.16 * sin(angle * 3 - time * 1.7 + lane * 0.22) +
                    0.07 * sin(angle * 5 + time * 1.1)) * wobbleMultiplier
            val radial = if (faceOn) 1 + wobble else 1.0
            val offset = if (faceOn) laneOffset else laneOffset + wobble
            val x = ux * cos(angle) + vx * sin(angle) + nx * offset
            val y = uy * cos(angle) + vy * sin(angle) + ny * offset
            val z = uz * cos(angle) + vz * sin(angle) + nz * offset
            val length = sqrt(x * x + y * y + z * z)
            val localRadius = baseRadius * radial
            val point =
                projector(
                    x / length * localRadius,
                    y / length * localRadius,
                    z / length * localRadius,
                )
            val depth = (point.z / radius + 1) / 2
            dots +=
                OrbDot(
                    point.x,
                    point.y,
                    point.z,
                    ((options["rBase"] ?: 1.1) + (options["rDepth"] ?: 1.7) * depth) *
                        (1 - 0.25 * edge) *
                        radiusScale,
                    0.52 - 0.44 * depth + 0.18 * edge,
                    0.4 + 0.6 * depth,
                )
        }
    }
    return finalize(dots, emptyList(), options["rMin"] ?: 0.3)
}

private class Polygon(private val vertices: List<Pair<Double, Double>>) {
    private val lengths =
        vertices.indices.map { index ->
            val start = vertices[index]
            val end = vertices[(index + 1) % vertices.size]
            hypot(end.first - start.first, end.second - start.second)
        }
    private val total = lengths.sum()

    fun at(fraction: Double): Pair<Double, Double> {
        var target = fraction * total
        var index = 0
        while (target > lengths[index] && index < vertices.lastIndex) {
            target -= lengths[index]
            index++
        }
        val start = vertices[index]
        val end = vertices[(index + 1) % vertices.size]
        val local = if (lengths[index] != 0.0) min(1.0, target / lengths[index]) else 0.0
        return lerp(start.first, end.first, local) to lerp(start.second, end.second, local)
    }
}

private val triangle = Polygon(listOf(0.0 to -0.26, 0.24 to 0.16, -0.24 to 0.16))
private val square =
    Polygon(listOf(0.0 to -0.2, 0.2 to -0.2, 0.2 to 0.2, -0.2 to 0.2, -0.2 to -0.2))

private fun shapePoint(shape: Int, fraction: Double): Pair<Double, Double> =
    when (shape) {
        0 -> {
            val angle = -PI / 2 + fraction * 2 * PI
            cos(angle) * 0.24 to sin(angle) * 0.24
        }
        1 -> triangle.at(fraction)
        else -> square.at(fraction)
    }

private fun morph(size: Double, time: Double, options: Map<String, Double>): OrbFrame {
    val hold = 1.4
    val transition = 0.9
    val segmentDuration = hold + transition
    var cycleTime = time % (segmentDuration * 3)
    if (cycleTime < 0) cycleTime += segmentDuration * 3
    val shape = floor(cycleTime / segmentDuration).toInt()
    val localTime = cycleTime - shape * segmentDuration
    val morph =
        if (localTime <= hold) {
            0.0
        } else {
            val progress = (localTime - hold) / transition
            progress * progress * (3 - 2 * progress)
        }
    val spread = options["spread"] ?: 1.0
    val sampleCount = 160
    val points =
        List(sampleCount) { index ->
            val fraction = index.toDouble() / sampleCount
            val start = shapePoint(shape, fraction)
            val end = shapePoint((shape + 1) % 3, fraction)
            lerp(start.first, end.first, morph) * spread to
                lerp(start.second, end.second, morph) * spread
        }
    val lengths =
        points.indices.map { index ->
            val start = points[index]
            val end = points[(index + 1) % points.size]
            hypot(end.first - start.first, end.second - start.second)
        }
    val total = lengths.sum()
    val dotCount = max(6, OrbEngine.jsRound(34 * (options["iconD"] ?: 1.0)).toInt())
    val radius = (options["rDot"] ?: 0.021) * 1.35 * spread
    val pulse = 1 + 0.02 * sin(localTime * 3.1)
    val center = size / 2
    var segment = 0
    var accumulated = 0.0
    val dots =
        List(dotCount) { index ->
            val target = index.toDouble() / dotCount * total
            while (accumulated + lengths[segment] < target && segment < sampleCount - 1) {
                accumulated += lengths[segment]
                segment++
            }
            val start = points[segment]
            val end = points[(segment + 1) % points.size]
            val fraction =
                if (lengths[segment] != 0.0) {
                    min(1.0, (target - accumulated) / lengths[segment])
                } else {
                    0.0
                }
            val x = lerp(start.first, end.first, fraction) * pulse
            val y = lerp(start.second, end.second, fraction) * pulse
            OrbDot(center + x * size, center + y * size, 0.0, max(0.35, radius * size), 0.1)
        }
    return finalize(dots, emptyList(), options["rMin"] ?: 0.25)
}
