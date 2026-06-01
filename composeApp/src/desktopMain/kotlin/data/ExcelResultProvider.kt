package data

import model.Match
import java.io.InputStream

class ExcelResultProvider(private val inputStream: InputStream) : ResultProvider {
    override fun getResults(): List<Match> {
        val reader = ExcelReader()
        // Using readParticipant because the ground truth Excel is expected to have the same format
        // where 'real' results are filled in the match columns.
        return reader.readParticipant(inputStream).predictions
    }
}
