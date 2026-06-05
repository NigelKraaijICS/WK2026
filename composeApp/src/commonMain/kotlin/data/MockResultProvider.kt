package data

import model.*

class MockResultProvider(private val structure: List<Match>) : ResultProvider {
    override fun getResults(): List<Match> {
        // Provide some dummy results for 2026
        return structure.map { match ->
            if (match.round == Round.GROUP) {
                match.copy(goals1 = (0..3).random(), goals2 = (0..3).random())
            } else {
                // For KO, we can't easily random without knowing teams,
                // but let's just say we don't have results yet or random them too
                match.copy(goals1 = (0..2).random(), goals2 = (0..2).random())
            }
        }
    }
}
