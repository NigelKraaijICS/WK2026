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

        // Find block start column
        val isMaster = participantName == "Results"
        var colOffset = if (isMaster) 1 else -1

        if (!isMaster) {
            val nameRow = sheet.getRow(2) // Row 3
            if (nameRow != null) {
                for (c in 1..200) {
                    val n = getCellValueAsString(nameRow.getCell(c))
                    if (n?.equals(participantName, ignoreCase = true) == true) {
                        colOffset = c - 1 // Start of block (Score 1 column)
                        break
                    }
                }
            }
            if (colOffset == -1) colOffset = 8 // Default to Anna if not found
        }

        // 1. Group Stage (Rows 6 to 87)
        for (i in 5..86) {
            val row = sheet.getRow(i) ?: continue
            // Identify match by Teams since ID column is only in block 0
            val t1n = getCellValueAsString(row.getCell(colOffset + 1))
            val t2n = getCellValueAsString(row.getCell(colOffset + 3))
            val s1 = getCellValueAsInt(row.getCell(colOffset))
            val s2 = getCellValueAsInt(row.getCell(colOffset + 2))

            if (t1n != null && t2n != null) {
                val team1 = Team(t1n)
                val team2 = Team(t2n)

                // Find match in structure
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

        // 2. Knockout Stage (Match 73 starts Row 90)
        for (i in 89..123) {
            val row = sheet.getRow(i) ?: continue
            val matchId = i - 89 + 73
            val matchStruct = structure.find { it.id == matchId } ?: continue

            // Scores at offset +8 and +9 (e.g. Anna starts C9, scores at C17,C18)
            val s1 = getCellValueAsInt(row.getCell(colOffset + 8))
            val s2 = getCellValueAsInt(row.getCell(colOffset + 9))

            // Teams for participants are usually at B+1, B+3 (formulas)
            val t1n = getCellValueAsString(row.getCell(colOffset + 1))
            val t2n = getCellValueAsString(row.getCell(colOffset + 3))

            predictions.add(matchStruct.copy(
                team1 = t1n?.takeIf { it.isNotBlank() }?.let { Team(it) } ?: matchStruct.team1,
                team2 = t2n?.takeIf { it.isNotBlank() }?.let { Team(it) } ?: matchStruct.team2,
                goals1 = s1,
                goals2 = s2
            ))
        }

        // 3. World Champion (Row 128)
        val championRow = sheet.getRow(127)
        if (championRow != null) {
            val champName = getCellValueAsString(championRow.getCell(colOffset + 1))
            if (champName != null && champName.isNotBlank()) {
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
