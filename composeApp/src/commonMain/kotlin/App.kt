import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import data.*
import logic.*
import model.*
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
fun App() {
    var participantFiles by remember { mutableStateOf(listOf<File>()) }
    var resultsFile by remember { mutableStateOf<File?>(null) }
    var scores by remember { mutableStateOf(listOf<Pair<String, Int>>()) }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }

    val reader = ExcelReader()
    val tournamentLogic = TournamentLogic()
    val scoringEngine = ScoringEngine()

    MaterialTheme {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("WK Pool 2026 Calculator", style = MaterialTheme.typography.h4)
            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    val chooser = JFileChooser().apply {
                        isMultiSelectionEnabled = true
                        fileFilter = FileNameExtensionFilter("Excel files", "xlsx")
                    }
                    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                        participantFiles = chooser.selectedFiles.toList()
                    }
                }) {
                    Text("Select Participant Files")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("${participantFiles.size} files selected")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    val chooser = JFileChooser().apply {
                        fileFilter = FileNameExtensionFilter("Excel files", "xlsx")
                    }
                    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                        resultsFile = chooser.selectedFile
                    }
                }) {
                    Text("Select Ground Truth Result File (Optional)")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(resultsFile?.name ?: "Using Mock/API results")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    isLoading = true
                    statusMessage = "Calculating..."
                    // Start calculation in a simple thread for now
                    Thread {
                        try {
                            val structureFile = File("WK-pool.xlsx")
                            if (!structureFile.exists()) {
                                statusMessage = "Error: WK-pool.xlsx not found in app directory"
                                isLoading = false
                                return@Thread
                            }

                            val (groups, structureMatches) = reader.readTournamentStructure(structureFile.inputStream())

                            val resultProvider: ResultProvider = if (resultsFile != null) {
                                ExcelResultProvider(resultsFile!!.inputStream())
                            } else {
                                MockResultProvider(structureMatches)
                            }

                            val rawResults = resultProvider.getResults()
                            val actualTournament = tournamentLogic.simulateTournament(structureMatches, groups, rawResults)

                            val calculatedScores = participantFiles.map { file ->
                                val participant = reader.readParticipant(file.inputStream())
                                val participantTournament = tournamentLogic.simulateTournament(structureMatches, groups, participant.predictions)
                                val score = scoringEngine.calculateScore(Participant(participant.name, participantTournament), actualTournament)
                                participant.name to score
                            }.sortedByDescending { it.second }

                            scores = calculatedScores
                            statusMessage = "Calculation complete"
                        } catch (e: Exception) {
                            e.printStackTrace()
                            statusMessage = "Error: ${e.message}"
                        } finally {
                            isLoading = false
                        }
                    }.start()
                },
                enabled = participantFiles.isNotEmpty() && !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) CircularProgressIndicator(color = MaterialTheme.colors.onPrimary)
                else Text("Calculate Scores")
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(statusMessage)

            Spacer(modifier = Modifier.height(16.dp))

            Text("Rankings:", style = MaterialTheme.typography.h6)
            Spacer(modifier = Modifier.height(8.dp))

            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(scores) { (name, score) ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), elevation = 2.dp) {
                            Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(name, style = MaterialTheme.typography.body1)
                                Text("$score pts", style = MaterialTheme.typography.body1)
                            }
                        }
                    }
                }
            }
        }
    }
}
