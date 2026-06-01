package data

import model.*
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.ss.usermodel.CellType
import java.io.InputStream

class ExcelReader {

    fun readTournamentStructure(inputStream: InputStream): Pair<List<Group>, List<Match>> {
        val workbook = WorkbookFactory.create(inputStream)
        val sheet = workbook.getSheet("Matches") ?: throw IllegalArgumentException("Sheet Matches not found")

        val allMatches = mutableListOf<Match>()
        val groupsMap = mutableMapOf<String, MutableList<Match>>()

        // Group Stage matches are 1 to 72
        // Knockout matches are 73 to 104

        for (i in 3..111) { // Up to Final
            val row = sheet.getRow(i) ?: continue
            val matchIdStr = getCellValueAsString(row.getCell(1))
            if (matchIdStr == null || !matchIdStr.all { it.isDigit() }) continue

            val matchId = matchIdStr.toInt()
            val team1Placeholder = getCellValueAsString(row.getCell(2)) ?: ""
            val team2Placeholder = getCellValueAsString(row.getCell(3)) ?: ""
            val team1Name = getCellValueAsString(row.getCell(8))
            val team2Name = getCellValueAsString(row.getCell(9))

            val round = when {
                matchId <= 72 -> Round.GROUP
                matchId <= 88 -> Round.ROUND_OF_32
                matchId <= 96 -> Round.ROUND_OF_16
                matchId <= 100 -> Round.QUARTER_FINAL
                matchId <= 102 -> Round.SEMI_FINAL
                matchId == 104 -> Round.FINAL
                matchId == 103 -> Round.GROUP // Third place, treat as group or skip
                else -> Round.GROUP
            }

            val match = Match(
                id = matchId,
                team1Placeholder = team1Placeholder,
                team2Placeholder = team2Placeholder,
                team1 = team1Name?.let { Team(it) },
                team2 = team2Name?.let { Team(it) },
                round = round
            )
            allMatches.add(match)

            if (round == Round.GROUP && team1Placeholder.isNotEmpty()) {
                val groupName = team1Placeholder.take(1)
                groupsMap.getOrPut(groupName) { mutableListOf() }.add(match)
            }
        }

        val groups = groupsMap.map { (name, matches) ->
            val teams = matches.flatMap { listOf(it.team1, it.team2) }.filterNotNull().distinct()
            Group(name, teams, matches)
        }

        workbook.close()
        return Pair(groups, allMatches)
    }

    fun readParticipant(inputStream: InputStream): Participant {
        val workbook = WorkbookFactory.create(inputStream)
        val sheet = workbook.getSheet("Predictions_1") ?: workbook.getSheet("Predictions_2")
            ?: throw IllegalArgumentException("Sheet Predictions_1 or Predictions_2 not found")

        val nameCell = sheet.getRow(2)?.getCell(8)
        val name = nameCell?.stringCellValue ?: "Unknown"

        val predictions = mutableListOf<Match>()

        for (i in 4..125) {
            val row = sheet.getRow(i) ?: continue

            val cell1Value = getCellValueAsString(row.getCell(1))
            if (cell1Value == "Round of 32" || cell1Value == "Round of 16" ||
                cell1Value == "Quarter final" || cell1Value == "Semi-Final" ||
                cell1Value == "Final" || cell1Value == "World Champion") continue

            // Determine if it's a match prediction
            val matchIdStr = cell1Value
            if (matchIdStr != null && matchIdStr.all { it.isDigit() }) {
                val matchId = matchIdStr.toInt()
                if (matchId <= 72) {
                    // Group Match
                    val team1Name = getCellValueAsString(row.getCell(2)) ?: ""
                    val goals1 = getCellValueAsInt(row.getCell(3))
                    val team2Name = getCellValueAsString(row.getCell(4)) ?: ""
                    val goals2 = getCellValueAsInt(row.getCell(10))

                    predictions.add(Match(
                        id = matchId,
                        team1Placeholder = "", team2Placeholder = "",
                        team1 = Team(team1Name), team2 = Team(team2Name),
                        goals1 = goals1, goals2 = goals2,
                        round = Round.GROUP
                    ))
                } else {
                    // Knockout Match
                    val team1Name = getCellValueAsString(row.getCell(8))
                    val goals1 = getCellValueAsInt(row.getCell(10))
                    val team2Name = getCellValueAsString(row.getCell(11))
                    val goals2 = getCellValueAsInt(row.getCell(12))

                    val round = when {
                        matchId <= 88 -> Round.ROUND_OF_32
                        matchId <= 96 -> Round.ROUND_OF_16
                        matchId <= 100 -> Round.QUARTER_FINAL
                        matchId <= 102 -> Round.SEMI_FINAL
                        matchId == 104 -> Round.FINAL
                        else -> Round.GROUP
                    }

                    if (team1Name != null && team2Name != null) {
                        predictions.add(Match(
                            id = matchId,
                            team1Placeholder = "", team2Placeholder = "",
                            team1 = Team(team1Name), team2 = Team(team2Name),
                            goals1 = goals1, goals2 = goals2,
                            round = round
                        ))
                    }
                }
            } else if (cell1Value == null && i == 125) {
                 // Champion cell is special
                 val teamName = getCellValueAsString(row.getCell(8))
                 if (teamName != null) {
                     predictions.add(Match(1000, "", "", Team(teamName), null, 1, 0, Round.CHAMPION))
                 }
            }
        }

        workbook.close()
        return Participant(name, predictions)
    }

    private fun getCellValueAsString(cell: org.apache.poi.ss.usermodel.Cell?): String? {
        if (cell == null) return null
        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue
            CellType.NUMERIC -> cell.numericCellValue.toInt().toString()
            CellType.FORMULA -> {
                try {
                    cell.stringCellValue
                } catch (e: Exception) {
                    cell.numericCellValue.toInt().toString()
                }
            }
            else -> null
        }
    }

    private fun getCellValueAsInt(cell: org.apache.poi.ss.usermodel.Cell?): Int? {
        if (cell == null) return null
        return when (cell.cellType) {
            CellType.NUMERIC -> cell.numericCellValue.toInt()
            CellType.STRING -> cell.stringCellValue.toIntOrNull()
            CellType.FORMULA -> {
                try {
                    cell.numericCellValue.toInt()
                } catch (e: Exception) {
                    cell.stringCellValue.toIntOrNull()
                }
            }
            else -> null
        }
    }
}
