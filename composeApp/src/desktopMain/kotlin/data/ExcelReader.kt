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

        // 1. Determine column offset for this participant
        var blockStartCol = -1
        val isMaster = participantName == "Results"

        if (isMaster) {
            blockStartCol = 1 // "Correct Results" block starts at Col B
        } else {
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
            if (blockStartCol == -1) blockStartCol = 8 // Default to Anna
        }

        // Relative offsets within a participant block (e.g., Anna's block I:U, index 8:20)
        // Match 1 Row 6 (Index 5):
        // Col I (8): Score 1 (15 - which is Team ID México)
        // Col J (9): Team 1 Name (México)
        // Col K (10): Score 2 (60 - which is Team ID South Africa)
        // Col L (11): Team 2 Name (South Africa)

        // Wait, the user says "I expect 1-1 and not 15-60".
        // 15 and 60 are actually the internal Team IDs.
        // If the participant HAS filled in a score, it will OVERWRITE these values in the cells.
        // So I should read from these same columns, but I must be careful not to treat IDs as scores.

        // 1. Group Stage (Rows 6 to 87)
        for (i in 5..86) {
            val row = sheet.getRow(i) ?: continue
            val idStr = getCellValueAsString(row.getCell(1))
            if (idStr == null || !idStr.all { it.isDigit() }) continue
            val matchId = idStr.toInt()
            if (matchId > 72) continue

            val matchStruct = structure.find { it.id == matchId } ?: continue

            // Read potential scores and team names from the participant's block
            val rawS1 = getCellValueAsInt(row.getCell(blockStartCol))
            val t1n = getCellValueAsString(row.getCell(blockStartCol + 1))
            val rawS2 = getCellValueAsInt(row.getCell(blockStartCol + 2))
            val t2n = getCellValueAsString(row.getCell(blockStartCol + 3))

            if (t1n != null && t2n != null) {
                val team1 = Team(t1n)
                val team2 = Team(t2n)

                // Heuristic: If the score is exactly equal to the team's internal ID from Groups sheet,
                // it's probably an unfilled placeholder. Mexico ID is 15, South Africa is 60.
                // However, if the user explicitly enters 15-60, we might misinterpret it.
                // But usually, scores are small (0-9).
                // Let's assume scores > 10 in the template are IDs if the match is not "played" yet.

                val s1 = if (rawS1 != null && rawS1 > 10 && !isMaster) null else rawS1
                val s2 = if (rawS2 != null && rawS2 > 10 && !isMaster) null else rawS2

                // Align scores with structure's team order
                val (finalS1, finalS2) = if (matchStruct.team1 == team1) s1 to s2 else if (matchStruct.team1 == team2) s2 to s1 else s1 to s2

                predictions.add(matchStruct.copy(
                    goals1 = finalS1,
                    goals2 = finalS2
                ))
            }
        }

        // 2. Knockout Stage (Match 73 starts Row 90)
        for (i in 89..123) {
            val row = sheet.getRow(i) ?: continue
            val matchId = i - 89 + 73
            val matchStruct = structure.find { it.id == matchId } ?: continue

            // For KO, the block seems different.
            // Anna block I:U (8:20).
            // In row 90, C17, C18 were 0. (Index 16, 17).
            // That's offset +8 and +9 from blockStartCol (8).
            val s1 = getCellValueAsInt(row.getCell(blockStartCol + 8))
            val s2 = getCellValueAsInt(row.getCell(blockStartCol + 9))

            // Teams for participants are usually at blockStartCol + 1 and blockStartCol + 3
            val t1n = getCellValueAsString(row.getCell(blockStartCol + 1))
            val t2n = getCellValueAsString(row.getCell(blockStartCol + 3))

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
            val champName = getCellValueAsString(championRow.getCell(blockStartCol + 1))
            if (champName != null && champName.isNotBlank() && champName != "World Champion") {
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
