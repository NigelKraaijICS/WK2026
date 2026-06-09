package data

import model.*
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.ss.usermodel.CellType
import java.io.InputStream
import kotlinx.datetime.*

data class MatchAnchor(val id: Int, val row: Int, val col: Int)

class ExcelReader {
    private var anchors: List<MatchAnchor> = emptyList()

    fun readTournamentStructure(inputStream: InputStream): Pair<List<Group>, List<Match>> {
        val workbook = WorkbookFactory.create(inputStream)

        // 1. Discover anchors in "World Cup" sheet
        val wcSheet = workbook.getSheet("World Cup") ?: throw IllegalArgumentException("World Cup sheet not found")
        val foundAnchors = mutableListOf<MatchAnchor>()
        for (r in 0..250) {
            val row = wcSheet.getRow(r) ?: continue
            for (c in 0..60) {
                val cell = row.getCell(c)
                val id = getCellValueAsInt(cell)
                if (id != null && id in 1..104) {
                    foundAnchors.add(MatchAnchor(id, r, c))
                }
            }
        }
        this.anchors = foundAnchors

        // 2. Read structure from "Matches" sheet
        val sheet = workbook.getSheet("Matches") ?: throw IllegalArgumentException("Sheet Matches not found")
        val allMatches = mutableListOf<Match>()
        val groupsMap = mutableMapOf<String, MutableList<Match>>()

        for (i in 1..200) {
            val row = sheet.getRow(i) ?: continue
            val matchIdStr = getCellValueAsString(row.getCell(1))
            if (matchIdStr == null || !matchIdStr.all { it.isDigit() } || matchIdStr.isEmpty()) continue

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

            val round = getRoundForMatch(matchId)

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

    private fun getRoundForMatch(matchId: Int): Round {
        return when {
            matchId <= 72 -> Round.GROUP
            matchId <= 88 -> Round.ROUND_OF_32
            matchId <= 96 -> Round.ROUND_OF_16
            matchId <= 100 -> Round.QUARTER_FINAL
            matchId <= 102 -> Round.SEMI_FINAL
            matchId == 104 -> Round.FINAL
            matchId == 103 -> Round.GROUP
            else -> Round.GROUP
        }
    }

    fun readMasterResults(inputStream: InputStream, structure: List<Match>): List<Match> {
        val workbook = WorkbookFactory.create(inputStream)
        val sheet = workbook.getSheet("World Cup") ?: return emptyList()
        val results = extractUsingAnchors(sheet, structure)
        workbook.close()
        return results
    }

    fun readParticipantFromFile(inputStream: InputStream, structure: List<Match>, filename: String): Participant {
        val workbook = WorkbookFactory.create(inputStream)

        // Exclusively use "World Cup" sheet for predictions and scores
        val sheet = workbook.getSheet("World Cup") ?: throw IllegalArgumentException("World Cup sheet not found in $filename")
        val matches = extractUsingAnchors(sheet, structure)

        // Champion prediction - scan around the typical area
        var champion: Team? = null
        for (r in 80..180) {
            val row = sheet.getRow(r) ?: continue
            for (c in 0..40) {
                val valStr = getCellValueAsString(row.getCell(c))
                if (valStr?.contains("World Champion", ignoreCase = true) == true) {
                    val teamName = getCellValueAsString(sheet.getRow(r + 1)?.getCell(c)) ?:
                                   getCellValueAsString(sheet.getRow(r)?.getCell(c + 1))
                    if (teamName != null && teamName.isNotBlank() && !teamName.contains("World Champion")) {
                        champion = Team(teamName)
                    }
                    break
                }
            }
            if (champion != null) break
        }

        val finalMatches = if (champion != null) matches + Match(1000, "", "", champion, null, 1, 0, Round.CHAMPION) else matches

        workbook.close()
        return Participant(filename, finalMatches)
    }

    private fun extractUsingAnchors(sheet: org.apache.poi.ss.usermodel.Sheet, structure: List<Match>): List<Match> {
        val matches = mutableListOf<Match>()
        for (anchor in anchors) {
            val matchStruct = structure.find { it.id == anchor.id } ?: continue

            // Teams at Row+2 relative to Match ID cell (e.g., ID at A11, Team Names at B13, C13)
            val teamRow = sheet.getRow(anchor.row + 2)
            val t1v = getCellValueAsString(teamRow?.getCell(anchor.col + 1))
            val t2v = getCellValueAsString(teamRow?.getCell(anchor.col + 2))

            // Scores at Row+3 relative to Match ID cell (e.g., ID at A11, Scores at B14, C14)
            val scoreRow = sheet.getRow(anchor.row + 3)
            val s1v = getCellValueAsString(scoreRow?.getCell(anchor.col + 1))
            val s2v = getCellValueAsString(scoreRow?.getCell(anchor.col + 2))

            val team1 = t1v?.takeIf { it.isNotBlank() && !it.all { char -> char.isDigit() } }?.let { Team(it) } ?: matchStruct.team1
            val team2 = t2v?.takeIf { it.isNotBlank() && !it.all { char -> char.isDigit() } }?.let { Team(it) } ?: matchStruct.team2

            // Goals - use numeric values. Avoid internal IDs (heuristic > 15)
            val s1 = s1v?.toIntOrNull()?.takeIf { it < 15 }
            val s2 = s2v?.toIntOrNull()?.takeIf { it < 15 }

            matches.add(matchStruct.copy(
                team1 = team1,
                team2 = team2,
                goals1 = s1,
                goals2 = s2
            ))
        }
        return matches
    }

    private fun getCellValueAsString(cell: org.apache.poi.ss.usermodel.Cell?): String? {
        if (cell == null) return null
        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue.trim()
            CellType.NUMERIC -> {
                val value = cell.numericCellValue
                if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
            }
            CellType.FORMULA -> {
                try {
                    val value = cell.numericCellValue
                    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
                } catch (e: Exception) {
                    try {
                        cell.stringCellValue.trim()
                    } catch (e2: Exception) { null }
                }
            }
            else -> null
        }
    }

    private fun getCellValueAsInt(cell: org.apache.poi.ss.usermodel.Cell?): Int? {
        val s = getCellValueAsString(cell)
        return s?.toIntOrNull()
    }
}
