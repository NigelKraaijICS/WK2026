package data

import model.Match

class ExcelResultProvider(
    private val inputStream: java.io.InputStream,
    private val structure: List<Match>,
    private val anchors: List<MatchAnchor>
) : ResultProvider {
    override fun getResults(): List<Match> {
        return ExcelReader().readMasterResults(inputStream, structure, anchors)
    }
}
