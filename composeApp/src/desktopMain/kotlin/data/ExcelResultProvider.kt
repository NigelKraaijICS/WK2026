package data

import model.Match
import java.io.InputStream

class ExcelResultProvider(
    private val inputStream: InputStream,
    private val structure: List<Match>
) : ResultProvider {
    override fun getResults(): List<Match> {
        val reader = ExcelReader()
        // For ground truth, name doesn't matter much but we'll use "Results"
        return reader.readParticipant(inputStream, structure, "Results").predictions
    }
}
