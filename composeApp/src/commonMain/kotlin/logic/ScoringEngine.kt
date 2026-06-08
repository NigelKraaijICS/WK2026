package logic

import model.*

class ScoringEngine {

    fun calculateScoreBreakdown(participant: Participant, actualResults: List<Match>): ScoreBreakdown {
        var totalScore = 0
        val matchScores = mutableListOf<MatchScoreInfo>()
        val advancementScores = mutableListOf<AdvancementScoreInfo>()
        val roundSummaries = mutableMapOf<Round, Int>()

        // 1. Match Scores (Outcome and Exact Score) - Applies only to Group Stage as per user request
        participant.predictions.filter { it.round == Round.GROUP }.forEach { prediction ->
            val actual = actualResults.find { it.id == prediction.id }
            var pointsForMatch = 0
            val explanations = mutableListOf<String>()

            if (actual != null && actual.goals1 != null && actual.goals2 != null &&
                prediction.goals1 != null && prediction.goals2 != null) {

                val actualResult = compareValues(actual.goals1, actual.goals2)
                val predResult = compareValues(prediction.goals1, prediction.goals2)

                if (actualResult == predResult) {
                    pointsForMatch += 2
                    explanations.add("Correct outcome (+2)")
                    if (actual.goals1 == prediction.goals1 && actual.goals2 == prediction.goals2) {
                        pointsForMatch += 3
                        explanations.add("Exact score bonus (+3)")
                    }
                }
            }

            if (explanations.isEmpty()) {
                if (actual?.goals1 == null) explanations.add("Match not yet played")
                else if (prediction.goals1 == null) explanations.add("No prediction made")
                else explanations.add("No points earned")
            }

            totalScore += pointsForMatch
            if (actual != null) {
                matchScores.add(MatchScoreInfo(actual, prediction.goals1, prediction.goals2, pointsForMatch, explanations.joinToString(", ")))
                roundSummaries[actual.round] = (roundSummaries[actual.round] ?: 0) + pointsForMatch
            }
        }

        // 2. Advancement Points (Cumulative)
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
                    val pts = round.points
                    totalScore += pts
                    advancementScores.add(AdvancementScoreInfo(team, round, pts, "Correctly predicted ${team.name} to reach ${round.displayName} (+ $pts)"))
                    roundSummaries[round] = (roundSummaries[round] ?: 0) + pts
                }
            }
        }

        // 3. World Champion
        val actualChampion = actualResults.find { it.round == Round.CHAMPION }?.team1
        val predictedChampion = participant.predictions.find { it.round == Round.CHAMPION }?.team1
        if (actualChampion != null && actualChampion == predictedChampion) {
            val pts = Round.CHAMPION.points
            totalScore += pts
            advancementScores.add(AdvancementScoreInfo(actualChampion, Round.CHAMPION, pts, "Correctly predicted World Champion (+ $pts)"))
            roundSummaries[Round.CHAMPION] = (roundSummaries[Round.CHAMPION] ?: 0) + pts
        }

        return ScoreBreakdown(totalScore, matchScores, advancementScores, roundSummaries)
    }

    private fun getTeamsAtRound(matches: List<Match>, round: Round): Set<Team> {
        return matches.filter { it.round == round }
            .flatMap { listOf(it.team1, it.team2) }
            .filterNotNull()
            .toSet()
    }
}
