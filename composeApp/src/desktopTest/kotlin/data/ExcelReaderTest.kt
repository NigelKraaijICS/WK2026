package data

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ExcelReaderTest {

    @Test
    fun testReadParticipant() {
        val excelFile = File("WK-pool.xlsx")
        if (!excelFile.exists()) {
            println("Skipping test because WK-pool.xlsx is not found")
            return
        }

        val reader = ExcelReader()
        val participant = reader.readParticipant(excelFile.inputStream())

        assertEquals("Anna", participant.name)
        assertTrue(participant.predictions.isNotEmpty())

        // Check first match (Mexico vs South Africa)
        val firstMatch = participant.predictions.find { it.id == 1 }
        assertNotNull(firstMatch)
        assertEquals("Mexico", firstMatch.team1?.name)
        assertEquals("South Africa", firstMatch.team2?.name)
        assertEquals(15, firstMatch.goals1)
        assertEquals(60, firstMatch.goals2)

        // Check a knockout match
        val koMatch = participant.predictions.find { it.id == 73 }
        assertNotNull(koMatch)
        assertEquals("Uzbekistan", koMatch.team1?.name)
        assertEquals("Colombia", koMatch.team2?.name)

        // Check champion
        val champion = participant.predictions.find { it.round == model.Round.CHAMPION }
        assertNotNull(champion)
        // In the original file, it might be empty or have a placeholder, but let's see what we got
        println("Champion prediction: ${champion.team1?.name}")
    }

    @Test
    fun testReadTournamentStructure() {
        val excelFile = File("WK-pool.xlsx")
        if (!excelFile.exists()) return

        val reader = ExcelReader()
        val (groups, matches) = reader.readTournamentStructure(excelFile.inputStream())

        assertEquals(12, groups.size) // A to L
        assertTrue(matches.size >= 104)

        val groupA = groups.find { it.name == "A" }
        assertNotNull(groupA)
        assertEquals(4, groupA.teams.size)
        assertTrue(groupA.teams.any { it.name == "Mexico" })
    }
}
