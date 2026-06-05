package logic

import model.*

class ScoringEngine {

    fun calculateScoreBreakdown(participant: Participant, actualResults: List<Match>): ScoreBreakdown {
        var totalScore = 0
        val matchScores = mutableListOf<MatchScoreInfo>()
        val advancementScores = mutableListOf<AdvancementScoreInfo>()

        // Match Scores (Group and Knockout)
        participant.predictions.filter { it.round != Round.CHAMPION }.forEach { prediction ->
            val actual = actualResults.find { it.id == prediction.id }
            var pointsForMatch = 0

            if (actual != null && actual.goals1 != null && actual.goals2 != null &&
                prediction.goals1 != null && prediction.goals2 != null &&
                actual.team1 == prediction.team1 && actual.team2 == prediction.team2) {

                val actualResult = compareValues(actual.goals1, actual.goals2)
                val predResult = compareValues(prediction.goals1, prediction.goals2)

                if (actualResult == predResult) {
                    pointsForMatch += 2
                    if (actual.goals1 == prediction.goals1 && actual.goals2 == prediction.goals2) {
                        pointsForMatch += 3
                    }
                }
            }

            totalScore += pointsForMatch
            if (actual != null) {
                matchScores.add(MatchScoreInfo(actual, prediction.goals1, prediction.goals2, pointsForMatch))
            }
        }

        // Advancement Points (Cumulative)
        val rounds = listOf(
            Round.ROUND_OF_32,
            Round.ROUND_OF_16,
            Round.QUARTER_FINAL,
            Round.SEMI_FINAL,
            Round.FINAL
        )

        rounds.forEach { round ->
            val actualTeams = getTeamsAtRound(actualResults, round)
            val predictedTeams = getTeamsAtRound(participant.predictions, round)

            predictedTeams.forEach { team ->
                if (actualTeams.contains(team)) {
                    totalScore += round.points
                    advancementScores.add(AdvancementScoreInfo(team, round, round.points))
                }
            }
        }

        // World Champion
        val actualChampion = actualResults.find { it.round == Round.CHAMPION }?.team1
        val predictedChampion = participant.predictions.find { it.round == Round.CHAMPION }?.team1
        if (actualChampion != null && actualChampion == predictedChampion) {
            totalScore += Round.CHAMPION.points
            advancementScores.add(AdvancementScoreInfo(actualChampion, Round.CHAMPION, Round.CHAMPION.points))
        }

        return ScoreBreakdown(totalScore, matchScores, advancementScores)
    }

    private fun getTeamsAtRound(matches: List<Match>, round: Round): Set<Team> {
        return matches.filter { it.round == round }
            .flatMap { listOf(it.team1, it.team2) }
            .filterNotNull()
            .toSet()
    }
}
