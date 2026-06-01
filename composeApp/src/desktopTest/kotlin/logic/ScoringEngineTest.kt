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
            Match(2, "", "", teamA, null, null, null, Round.ROUND_OF_32), // In KO, they only need to predict the team. If they don't have scores, no match points.
            Match(3, "", "", teamA, null, 1, 0, Round.CHAMPION) // Correct Champion: 10
        ))

        val engine = ScoringEngine()
        val score = engine.calculateScore(participant, actualResults)

        // Match 1: 5 pts
        // Match 2: 2 pts (Advancement to R32)
        // Match 3: 10 pts (Champion)
        // Total: 17
        assertEquals(17, score)
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
        val score = engine.calculateScore(participant, actualResults)
        assertEquals(5, score) // 2 (R32) + 3 (R16)
    }

    @Test
    fun testExactScoreInKO() {
        val teamA = Team("A")
        val teamB = Team("B")
        val actualResults = listOf(
            Match(73, "", "", teamA, teamB, 2, 1, Round.ROUND_OF_32)
        )
        val participant = Participant("Test", listOf(
            Match(73, "", "", teamA, teamB, 2, 1, Round.ROUND_OF_32)
        ))

        val engine = ScoringEngine()
        val score = engine.calculateScore(participant, actualResults)
        // Match points: 2 (win) + 3 (exact) = 5
        // Advancement: 2 (teamA reached R32) + 2 (teamB reached R32) = 4
        // Total: 9
        assertEquals(9, score)
    }
}
