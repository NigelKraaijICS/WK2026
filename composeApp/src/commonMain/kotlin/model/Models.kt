package model

enum class Round(val displayName: String, val points: Int) {
    GROUP("Group Stage", 0),
    ROUND_OF_32("Round of 32", 2),
    ROUND_OF_16("Round of 16", 3),
    QUARTER_FINAL("Quarter Finals", 4),
    SEMI_FINAL("Semi Finals", 6),
    FINAL("Final", 8),
    CHAMPION("World Champion", 10)
}

data class Team(val name: String) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Team) return false
        return name.trim().equals(other.name.trim(), ignoreCase = true)
    }

    override fun hashCode(): Int {
        return name.trim().lowercase().hashCode()
    }
}

data class Match(
    val id: Int,
    val team1Placeholder: String,
    val team2Placeholder: String,
    val team1: Team? = null,
    val team2: Team? = null,
    val goals1: Int? = null,
    val goals2: Int? = null,
    val round: Round
)

data class Group(
    val name: String,
    val teams: List<Team>,
    val matches: List<Match>
)

data class ScoreBreakdown(
    val totalScore: Int,
    val matchScores: List<MatchScoreInfo>,
    val advancementScores: List<AdvancementScoreInfo>,
    val roundSummaries: Map<Round, Int>
)

data class MatchScoreInfo(
    val match: Match,
    val predictedGoals1: Int?,
    val predictedGoals2: Int?,
    val points: Int,
    val explanation: String
)

data class AdvancementScoreInfo(
    val team: Team,
    val round: Round,
    val points: Int,
    val explanation: String
)

data class Participant(
    val name: String,
    val predictions: List<Match>
)

data class Standing(
    val team: Team,
    var played: Int = 0,
    var won: Int = 0,
    var drawn: Int = 0,
    var lost: Int = 0,
    var goalsFor: Int = 0,
    var goalsAgainst: Int = 0,
    var points: Int = 0
) {
    val goalDifference: Int get() = goalsFor - goalsAgainst
}
