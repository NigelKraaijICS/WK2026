package logic

import model.*

class ScoringEngine {

    fun calculateScore(participant: Participant, actualResults: List<Match>): Int {
        var totalScore = 0

        // Match Scores (Group stage only)
        // Correct prediction of win/draw/loss = 2 pts
        // Correct exact score = +3 pts
        participant.predictions.filter { it.round == Round.GROUP }.forEach { prediction ->
            val actual = actualResults.find { it.id == prediction.id }
            if (actual != null && actual.goals1 != null && actual.goals2 != null &&
                prediction.goals1 != null && prediction.goals2 != null) {

                val actualResult = compareValues(actual.goals1, actual.goals2)
                val predResult = compareValues(prediction.goals1, prediction.goals2)

                if (actualResult == predResult) {
                    totalScore += 2
                    if (actual.goals1 == prediction.goals1 && actual.goals2 == prediction.goals2) {
                        totalScore += 3
                    }
                }
            }
        }

        // Advancement Points (Cumulative)
        val advancementRounds = listOf(
            Round.ROUND_OF_32,
            Round.ROUND_OF_16,
            Round.QUARTER_FINAL,
            Round.SEMI_FINAL,
            Round.FINAL
        )

        advancementRounds.forEach { round ->
            val actualTeams = getTeamsAtRound(actualResults, round)
            val predictedTeams = getTeamsAtRound(participant.predictions, round)

            predictedTeams.forEach { team ->
                if (actualTeams.contains(team)) {
                    totalScore += round.points
                }
            }
        }

        // World Champion
        val actualChampion = actualResults.find { it.round == Round.CHAMPION }?.team1
        val predictedChampion = participant.predictions.find { it.round == Round.CHAMPION }?.team1
        if (actualChampion != null && actualChampion == predictedChampion) {
            totalScore += Round.CHAMPION.points
        }

        return totalScore
    }

    private fun getTeamsAtRound(matches: List<Match>, round: Round): Set<Team> {
        return matches.filter { it.round == round }
            .flatMap { listOf(it.team1, it.team2) }
            .filterNotNull()
            .toSet()
    }
}
