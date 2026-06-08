package data

import model.*
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.ss.usermodel.CellType
import java.io.InputStream
import kotlinx.datetime.*

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

            val date = try {
                val cell = row.getCell(4)
                if (cell?.cellType == CellType.NUMERIC) {
                    val javaDate = cell.dateCellValue
                    javaDate.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime().toKotlinLocalDateTime()
                } else null
            } catch (e: Exception) { null }

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
                round = round,
                date = date
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

    fun readParticipant(inputStream: InputStream, structure: List<Match>, participantName: String): Participant {
        val workbook = WorkbookFactory.create(inputStream)
        val sheet = workbook.getSheet("Predictions_1") ?: workbook.getSheet("Predictions_2")
            ?: throw IllegalArgumentException("Sheet Predictions_1 or Predictions_2 not found")

        val predictions = mutableListOf<Match>()

        // 1. Find the column block for the participant
        var blockStartCol = -1
        val nameRow = sheet.getRow(2) // Row 3
        if (nameRow != null) {
            for (c in 1..200) {
                val n = getCellValueAsString(nameRow.getCell(c))
                if (n?.equals(participantName, ignoreCase = true) == true) {
                    blockStartCol = c
                    break
                }
            }
        }

        // If not found, and name is "Results", try "Correct Results" or index 1
        if (blockStartCol == -1 && participantName == "Results") blockStartCol = 1
        // Default to Anna if still not found
        if (blockStartCol == -1) blockStartCol = 8

        // Based on analysis:
        // C(blockStartCol): ID/Title (usually empty in data rows)
        // C(blockStartCol + 1): Score 1
        // C(blockStartCol + 2): Team 1 Name
        // C(blockStartCol + 3): Score 2
        // C(blockStartCol + 4): Team 2 Name

        // 1. Group Stage: Rows 6 to 87
        for (i in 5..86) {
            val row = sheet.getRow(i) ?: continue
            val t1n = getCellValueAsString(row.getCell(blockStartCol + 2))
            val t2n = getCellValueAsString(row.getCell(blockStartCol + 4))
            val s1 = getCellValueAsInt(row.getCell(blockStartCol + 1))
            val s2 = getCellValueAsInt(row.getCell(blockStartCol + 3))

            if (t1n != null && t2n != null && t1n.isNotBlank() && t2n.isNotBlank()) {
                val team1 = Team(t1n)
                val team2 = Team(t2n)

                val matchStruct = structure.find {
                    it.round == Round.GROUP &&
                    ((it.team1 == team1 && it.team2 == team2) || (it.team1 == team2 && it.team2 == team1))
                }

                if (matchStruct != null) {
                    val (finalS1, finalS2) = if (matchStruct.team1 == team1) s1 to s2 else s2 to s1
                    predictions.add(matchStruct.copy(goals1 = finalS1, goals2 = finalS2))
                }
            }
        }

        // 2. Knockout Stage: Row 90 onwards
        for (i in 89..124) {
            val row = sheet.getRow(i) ?: continue
            val t1n = getCellValueAsString(row.getCell(blockStartCol + 2))
            val t2n = getCellValueAsString(row.getCell(blockStartCol + 4))
            val s1 = getCellValueAsInt(row.getCell(blockStartCol + 1))
            val s2 = getCellValueAsInt(row.getCell(blockStartCol + 3))

            if (t1n != null && t1n.isNotBlank() && t2n != null && t2n.isNotBlank()) {
                // Match ID based on round/offset
                val matchId = when {
                    i <= 104 -> 73 + (i - 89)
                    i <= 113 -> 89 + (i - 106)
                    i <= 118 -> 97 + (i - 115)
                    i <= 121 -> 101 + (i - 120)
                    i == 124 -> 104
                    else -> 0
                }

                if (matchId > 0) {
                    val matchStruct = structure.find { it.id == matchId }
                    predictions.add(Match(
                        id = matchId,
                        team1Placeholder = matchStruct?.team1Placeholder ?: "",
                        team2Placeholder = matchStruct?.team2Placeholder ?: "",
                        team1 = Team(t1n),
                        team2 = Team(t2n),
                        goals1 = s1,
                        goals2 = s2,
                        round = matchStruct?.round ?: Round.GROUP,
                        date = matchStruct?.date
                    ))
                }
            }
        }

        // 3. World Champion (Row 128 - index 127)
        val championRow = sheet.getRow(127)
        if (championRow != null) {
            val champName = getCellValueAsString(championRow.getCell(blockStartCol + 2)) ?: getCellValueAsString(championRow.getCell(blockStartCol))
            if (champName != null && champName != "World Champion" && champName.isNotBlank()) {
                predictions.add(Match(1000, "", "", Team(champName), null, 1, 0, Round.CHAMPION))
            }
        }

        workbook.close()
        return Participant(participantName, predictions)
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
                    try {
                        cell.numericCellValue.toLong().toString()
                    } catch (e2: Exception) { null }
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
