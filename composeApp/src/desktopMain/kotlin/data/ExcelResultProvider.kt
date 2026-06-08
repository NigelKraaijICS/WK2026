package data

import model.Match
import java.io.InputStream

class ExcelResultProvider(
    private val inputStream: InputStream,
    private val structure: List<Match>
) : ResultProvider {
    override fun getResults(): List<Match> {
        val reader = ExcelReader()
        return reader.readMasterResults(inputStream, structure)
    }
}
