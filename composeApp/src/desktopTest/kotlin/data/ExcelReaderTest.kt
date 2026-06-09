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
        val (groups, structure) = reader.readTournamentStructure(excelFile.inputStream())
        val participant = reader.readParticipantFromFile(excelFile.inputStream(), structure, "TestFile")

        assertEquals("TestFile", participant.name)
        assertTrue(participant.predictions.isNotEmpty())
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
    }
}
