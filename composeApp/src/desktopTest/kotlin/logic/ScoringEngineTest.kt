package logic

import model.*
import kotlin.test.Test
import kotlin.test.assertEquals

class ScoringEngineTest {

    @Test
    fun testCalculateScore() {
        val teamA = Team("A")
        val teamB = Team("B")

        val actualResults = listOf(
            Match(1, "", "", teamA, teamB, 2, 1, Round.GROUP), // A wins
            Match(2, "", "", teamA, null, 1, 0, Round.ROUND_OF_32), // A reached R32
            Match(3, "", "", teamA, null, 1, 0, Round.CHAMPION) // A is Champion
        )

        val participant = Participant("Test", listOf(
            Match(1, "", "", teamA, teamB, 2, 1, Round.GROUP), // Correct exact: 2+3 = 5
            Match(2, "", "", teamA, null, null, null, Round.ROUND_OF_32),
            Match(3, "", "", teamA, null, 1, 0, Round.CHAMPION) // Correct Champion: 10
        ))

        val engine = ScoringEngine()
        val breakdown = engine.calculateScoreBreakdown(participant, actualResults, Round.values().toSet())

        assertEquals(17, breakdown.totalScore)
    }

    @Test
    fun testCumulativeAdvancement() {
        val teamA = Team("A")
        val actualResults = listOf(
            Match(1, "", "", teamA, null, 1, 0, Round.ROUND_OF_32),
            Match(2, "", "", teamA, null, 1, 0, Round.ROUND_OF_16)
        )
        val participant = Participant("Test", listOf(
            Match(1, "", "", teamA, null, null, null, Round.ROUND_OF_32),
            Match(2, "", "", teamA, null, null, null, Round.ROUND_OF_16)
        ))

        val engine = ScoringEngine()
        val breakdown = engine.calculateScoreBreakdown(participant, actualResults, Round.values().toSet())
        assertEquals(5, breakdown.totalScore) // 2 (R32) + 3 (R16)
    }

    @Test
    fun testNoExactScoreInKO() {
        val teamA = Team("A")
        val teamB = Team("B")
        val actualResults = listOf(
            Match(73, "", "", teamA, teamB, 2, 1, Round.ROUND_OF_32)
        )
        val participant = Participant("Test", listOf(
            Match(73, "", "", teamA, teamB, 2, 1, Round.ROUND_OF_32)
        ))

        val engine = ScoringEngine()
        val breakdown = engine.calculateScoreBreakdown(participant, actualResults, Round.values().toSet())

        // Rule: After group stage, only advancement matters.
        // Match points: 0
        // Advancement: 2 (teamA reached R32) + 2 (teamB reached R32) = 4
        // Total: 4
        assertEquals(4, breakdown.totalScore)
    }
}
