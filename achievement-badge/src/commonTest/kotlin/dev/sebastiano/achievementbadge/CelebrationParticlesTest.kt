package dev.sebastiano.achievementbadge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CelebrationParticlesTest {
    @Test
    fun confettiIsTheSameEveryReplay() {
        assertEquals(confettiPieces(), confettiPieces())
    }

    @Test
    fun confettiLeavesFromTheBadgeEdgeAndThenFades() {
        for (piece in confettiPieces()) {
            val start = piece.stateAt(0f)
            assertEquals(piece.spawnRadius, start.distanceFromCenter(), 0.01f)
            assertEquals(0f, piece.stateAt(Confetti.LIFETIME + 0.01f).alpha)
        }
    }

    @Test
    fun twinklesComeAndGo() {
        val shown = (0 until 400).map { twinklesAt(it / 100f) }
        assertTrue(shown.any { it.isNotEmpty() })
        assertTrue(shown.flatten().all { it.scale in 0f..1f })
        assertTrue(twinklesAt(0f).isEmpty())
    }
}
