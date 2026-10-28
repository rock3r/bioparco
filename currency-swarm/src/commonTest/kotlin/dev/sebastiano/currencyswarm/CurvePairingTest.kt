package dev.sebastiano.currencyswarm

import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CurvePairingTest {
    @Test
    fun hilbertIndexVisitsEveryCellOnceThroughNeighbours() {
        val side = 16
        val cells = buildList {
            for (x in 0 until side) for (y in 0 until side) add(
                Triple(hilbertIndex(x, y, side), x, y)
            )
        }
        assertEquals((0 until side * side).toList(), cells.map { it.first }.sorted())
        cells
            .sortedBy { it.first }
            .zipWithNext()
            .forEach { (a, b) ->
                val step = abs(a.second - b.second) + abs(a.third - b.third)
                assertEquals(1, step, "cells ${a.first} and ${b.first} are not neighbours")
            }
    }

    @Test
    fun pairingIsAPermutation() {
        val random = Random(3)
        val from = FloatArray(400) { random.nextFloat() * 2f - 1f }
        val to = FloatArray(400) { random.nextFloat() * 2f - 1f }
        val pairs = pairAlongCurve(from, to)
        assertEquals((0 until 200).toList(), pairs.sorted())
    }

    @Test
    fun theSameShapeShuffledPairsEveryPointWithItself() {
        val random = Random(5)
        val count = 300
        val from = FloatArray(count * 2) { random.nextFloat() - 0.5f }
        val order = (0 until count).shuffled(random)
        val to = FloatArray(count * 2)
        order.forEachIndexed { slot, source ->
            to[slot * 2] = from[source * 2]
            to[slot * 2 + 1] = from[source * 2 + 1]
        }
        val pairs = pairAlongCurve(from, to)
        for (i in 0 until count) {
            assertEquals(from[i * 2], to[pairs[i] * 2])
            assertEquals(from[i * 2 + 1], to[pairs[i] * 2 + 1])
        }
    }

    @Test
    fun pairingKeepsTravelShortWhenAShapeShifts() {
        val random = Random(11)
        val count = 500
        val from = FloatArray(count * 2) { random.nextFloat() - 0.5f }
        // The same cloud, nudged right and reshuffled: a good pairing moves each dot a little.
        val to = FloatArray(count * 2)
        (0 until count).shuffled(random).forEachIndexed { slot, source ->
            to[slot * 2] = from[source * 2] + 0.05f
            to[slot * 2 + 1] = from[source * 2 + 1]
        }
        val pairs = pairAlongCurve(from, to)
        val curve = travel(from, to) { pairs[it] }
        val byIndex = travel(from, to) { it }
        assertTrue(curve < byIndex / 2f, "curve travel $curve vs index travel $byIndex")
    }

    private fun travel(from: FloatArray, to: FloatArray, target: (Int) -> Int): Float {
        var sum = 0f
        for (i in 0 until from.size / 2) {
            val j = target(i)
            sum += abs(from[i * 2] - to[j * 2]) + abs(from[i * 2 + 1] - to[j * 2 + 1])
        }
        return sum
    }
}
