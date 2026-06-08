package logic

import model.*

class TournamentLogic {

    fun calculateGroupStandings(group: Group, matches: List<Match>): List<Standing> {
        val standings = group.teams.associateWith { Standing(it) }.toMutableMap()

        group.matches.forEach { matchPlaceholder ->
            val actualMatch = matches.find { it.id == matchPlaceholder.id } ?: return@forEach
            val g1 = actualMatch.goals1
            val g2 = actualMatch.goals2
            val t1 = actualMatch.team1
            val t2 = actualMatch.team2

            if (g1 != null && g2 != null && t1 != null && t2 != null) {
                val s1 = standings[t1] ?: return@forEach
                val s2 = standings[t2] ?: return@forEach

                s1.played++
                s2.played++
                s1.goalsFor += g1
                s1.goalsAgainst += g2
                s2.goalsFor += g2
                s2.goalsAgainst += g1

                when {
                    g1 > g2 -> {
                        s1.points += 3
                        s1.won++
                        s2.lost++
                    }
                    g1 < g2 -> {
                        s2.points += 3
                        s2.won++
                        s1.lost++
                    }
                    else -> {
                        s1.points += 1
                        s2.points += 1
                        s1.drawn++
                        s2.drawn++
                    }
                }
            }
        }

        return standings.values.sortedWith(
            compareByDescending<Standing> { it.points }
                .thenByDescending { it.goalDifference }
                .thenByDescending { it.goalsFor }
        )
    }

    fun determineAdvancingTeams(groups: List<Group>, allMatches: List<Match>): Map<String, Team> {
        val advancingTeams = mutableMapOf<String, Team>()
        val allStandings = groups.map { calculateGroupStandings(it, allMatches) }

        allStandings.forEachIndexed { index, standings ->
            val groupName = groups[index].name
            if (standings.size >= 1) advancingTeams["1$groupName"] = standings[0].team
            if (standings.size >= 2) advancingTeams["2$groupName"] = standings[1].team
        }

        val thirdPlaced = allStandings.filter { it.size >= 3 }.map { it[2] }
            .sortedWith(
                compareByDescending<Standing> { it.points }
                    .thenByDescending { it.goalDifference }
                    .thenByDescending { it.goalsFor }
            )

        val thirdPlacePlaceholderNames = listOf(
            "3-ABCDF", "3-ABCDE", "3-ADEFG", "3-ABCGH", "3-BCDEG", "3-BCDFG", "3-CEFHI", "3-CDFGH",
            "3-ABCHI", "3-ABCFG", "3-ADEGH", "3-ACEFG", "3-ABCEF", "3-ABDEG", "3-BCDFH", "3-BCDFI"
        ).distinct()

        thirdPlaced.take(8).forEachIndexed { index, standing ->
            if (index < thirdPlacePlaceholderNames.size) {
                advancingTeams[thirdPlacePlaceholderNames[index]] = standing.team
            }
        }

        return advancingTeams
    }

    fun simulateTournament(structure: List<Match>, groups: List<Group>, results: List<Match>): List<Match> {
        val fullTournament = structure.map { match ->
            val result = results.find { it.id == match.id }
            if (result != null) {
                match.copy(
                    team1 = result.team1 ?: match.team1,
                    team2 = result.team2 ?: match.team2,
                    goals1 = result.goals1,
                    goals2 = result.goals2
                )
            } else match
        }.toMutableList()

        val advancingFromGroups = determineAdvancingTeams(groups, fullTournament.filter { it.round == Round.GROUP })

        // Update R32
        for (i in fullTournament.indices) {
            val match = fullTournament[i]
            if (match.round == Round.ROUND_OF_32) {
                val t1 = match.team1 ?: advancingFromGroups[match.team1Placeholder]
                val t2 = match.team2 ?: advancingFromGroups[match.team2Placeholder]
                fullTournament[i] = match.copy(team1 = t1, team2 = t2)
            }
        }

        // Propagate winners
        val rounds = listOf(Round.ROUND_OF_32, Round.ROUND_OF_16, Round.QUARTER_FINAL, Round.SEMI_FINAL, Round.FINAL)

        for (round in rounds) {
            val roundMatches = fullTournament.filter { it.round == round }
            roundMatches.forEach { match ->
                // If the results already specify a team1 and team2 for the NEXT round match that corresponds to this one,
                // we should respect that over the simulation.
                // But the simulator's job is to fill in the gaps.
                val winner = determineWinner(match)
                if (winner != null) {
                    val nextMatchPlaceholder = "W${match.id}"
                    for (j in fullTournament.indices) {
                        val m = fullTournament[j]
                        if (m.team1Placeholder == nextMatchPlaceholder && m.team1 == null) {
                            fullTournament[j] = m.copy(team1 = winner)
                        } else if (m.team2Placeholder == nextMatchPlaceholder && m.team2 == null) {
                            fullTournament[j] = m.copy(team2 = winner)
                        }
                    }
                    if (round == Round.FINAL) {
                        fullTournament.add(Match(1000, "", "", winner, null, 1, 0, Round.CHAMPION))
                    }
                }
            }
        }

        return fullTournament
    }

    private fun determineWinner(match: Match): Team? {
        val g1 = match.goals1 ?: return null
        val g2 = match.goals2 ?: return null
        return when {
            g1 > g2 -> match.team1
            g1 < g2 -> match.team2
            else -> {
                // In knockouts, there's always a winner. If the score is even,
                // we'd need penalty shoot-out results. For now, we return Team 1
                // unless Team 2 is explicitly set in the next round.
                match.team1
            }
        }
    }
}
