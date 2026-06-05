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

        for (i in 3..111) {
            val row = sheet.getRow(i) ?: continue
            val matchIdStr = getCellValueAsString(row.getCell(1))
            if (matchIdStr == null || !matchIdStr.all { it.isDigit() }) continue

            val matchId = matchIdStr.toInt()
            val t1p = getCellValueAsString(row.getCell(2)) ?: ""
            val t2p = getCellValueAsString(row.getCell(3)) ?: ""

            val t1n = getCellValueAsString(row.getCell(8))
            val t2n = getCellValueAsString(row.getCell(9))

            val round = when {
                matchId <= 72 -> Round.GROUP
                matchId <= 88 -> Round.ROUND_OF_32
                matchId <= 96 -> Round.ROUND_OF_16
                matchId <= 100 -> Round.QUARTER_FINAL
                matchId <= 102 -> Round.SEMI_FINAL
                matchId == 104 -> Round.FINAL
                else -> Round.GROUP
            }

            val match = Match(
                id = matchId,
                team1Placeholder = t1p,
                team2Placeholder = t2p,
                team1 = t1n?.takeIf { it.isNotBlank() }?.let { Team(it) },
                team2 = t2n?.takeIf { it.isNotBlank() }?.let { Team(it) },
                round = round
            )
            allMatches.add(match)

            if (round == Round.GROUP && t1p.isNotEmpty()) {
                val groupName = t1p.take(1)
                groupsMap.getOrPut(groupName) { mutableListOf() }.add(match)
            }
        }

        workbook.close()
        return Pair(groupsMap.map { (name, matches) ->
            val teams = matches.flatMap { listOf(it.team1, it.team2) }.filterNotNull().distinct()
            Group(name, teams, matches)
        }, allMatches)
    }

    fun readParticipant(inputStream: InputStream, structure: List<Match>): Participant {
        val workbook = WorkbookFactory.create(inputStream)
        val sheet = workbook.getSheet("Predictions_1") ?: workbook.getSheet("Predictions_2")
            ?: throw IllegalArgumentException("Sheet Predictions_1 or Predictions_2 not found")

        val nameCell = sheet.getRow(2)?.getCell(8)
        val name = nameCell?.let { getCellValueAsString(it) } ?: "Unknown"

        val predictions = mutableListOf<Match>()

        // Participant columns in Predictions_1 (based on analysis):
        // Col 8: Score 1, Col 9: Team 1 Name, Col 10: Score 2, Col 11: Team 2 Name

        // 1. Group Stage: Rows 5 to 87 (indices 4 to 86)
        for (i in 4..86) {
            val row = sheet.getRow(i) ?: continue
            val t1Name = getCellValueAsString(row.getCell(9))
            val t2Name = getCellValueAsString(row.getCell(11))
            val s1 = getCellValueAsInt(row.getCell(8))
            val s2 = getCellValueAsInt(row.getCell(10))

            if (t1Name != null && t1Name.isNotBlank() && t2Name != null && t2Name.isNotBlank()) {
                val team1 = Team(t1Name)
                val team2 = Team(t2Name)

                val matchStruct = structure.find {
                    it.round == Round.GROUP &&
                    ((it.team1 == team1 && it.team2 == team2) || (it.team1 == team2 && it.team2 == team1))
                }

                if (matchStruct != null) {
                    // Match predicted scores to the structure's team order
                    val (finalS1, finalS2) = if (matchStruct.team1 == team1) s1 to s2 else s2 to s1
                    predictions.add(Match(
                        id = matchStruct.id,
                        team1Placeholder = matchStruct.team1Placeholder,
                        team2Placeholder = matchStruct.team2Placeholder,
                        team1 = matchStruct.team1,
                        team2 = matchStruct.team2,
                        goals1 = finalS1,
                        goals2 = finalS2,
                        round = Round.GROUP
                    ))
                }
            }
        }

        // 2. Knockout Stage: Rows 85 to 124
        for (i in 84..123) {
            val row = sheet.getRow(i) ?: continue
            val t1Name = getCellValueAsString(row.getCell(9))
            val t2Name = getCellValueAsString(row.getCell(11))
            val s1 = getCellValueAsInt(row.getCell(8))
            val s2 = getCellValueAsInt(row.getCell(10))

            if (t1Name != null && t1Name.isNotBlank() && t2Name != null && t2Name.isNotBlank()) {
                val team1 = Team(t1Name)
                val team2 = Team(t2Name)

                val round = when {
                    i <= 104 -> Round.ROUND_OF_32
                    i <= 113 -> Round.ROUND_OF_16
                    i <= 118 -> Round.QUARTER_FINAL
                    i <= 121 -> Round.SEMI_FINAL
                    i <= 123 -> Round.FINAL
                    else -> Round.GROUP
                }

                // For KO, the row corresponds to a specific match ID in our structure
                val matchId = when(round) {
                    Round.ROUND_OF_32 -> 73 + (i - 89)
                    Round.ROUND_OF_16 -> 89 + (i - 106)
                    Round.QUARTER_FINAL -> 97 + (i - 115)
                    Round.SEMI_FINAL -> 101 + (i - 120)
                    Round.FINAL -> 104
                    else -> 0
                }

                if (matchId > 0) {
                    predictions.add(Match(
                        id = matchId,
                        team1Placeholder = "", team2Placeholder = "",
                        team1 = team1,
                        team2 = team2,
                        goals1 = s1,
                        goals2 = s2,
                        round = round
                    ))
                }
            }
        }

        // 3. World Champion
        val championRow = sheet.getRow(125)
        if (championRow != null) {
            val champName = getCellValueAsString(championRow.getCell(8))
            if (champName != null && champName.isNotBlank()) {
                predictions.add(Match(1000, "", "", Team(champName), null, 1, 0, Round.CHAMPION))
            }
        }

        workbook.close()
        return Participant(name, predictions)
    }

    private fun getCellValueAsString(cell: org.apache.poi.ss.usermodel.Cell?): String? {
        if (cell == null) return null
        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue.trim()
            CellType.NUMERIC -> cell.numericCellValue.toLong().toString()
            CellType.FORMULA -> {
                try {
                    cell.stringCellValue.trim()
                } catch (e: Exception) {
                    cell.numericCellValue.toLong().toString()
                }
            }
            else -> null
        }
    }

    private fun getCellValueAsInt(cell: org.apache.poi.ss.usermodel.Cell?): Int? {
        if (cell == null) return null
        return when (cell.cellType) {
            CellType.NUMERIC -> cell.numericCellValue.toInt()
            CellType.STRING -> cell.stringCellValue.trim().toIntOrNull()
            CellType.FORMULA -> {
                try {
                    cell.numericCellValue.toInt()
                } catch (e: Exception) {
                    cell.stringCellValue.trim().toIntOrNull()
                }
            }
            else -> null
        }
    }
}
