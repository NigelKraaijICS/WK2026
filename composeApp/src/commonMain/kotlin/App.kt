import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import data.*
import logic.*
import model.*
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

private val DarkGray = Color(0xFF121212)
private val SurfaceGray = Color(0xFF1E1E1E)
private val PrimaryGold = Color(0xFFFFD700)
private val SuccessGreen = Color(0xFF4CAF50)

@Composable
fun App() {
    var participantFiles by remember { mutableStateOf(listOf<File>()) }
    var resultsFile by remember { mutableStateOf<File?>(null) }
    var rankings by remember { mutableStateOf(listOf<Pair<String, ScoreBreakdown>>()) }
    var selectedParticipant by remember { mutableStateOf<Pair<String, ScoreBreakdown>?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }

    val reader = ExcelReader()
    val tournamentLogic = TournamentLogic()
    val scoringEngine = ScoringEngine()

    MaterialTheme(
        colors = darkColors(
            primary = PrimaryGold,
            background = DarkGray,
            surface = SurfaceGray,
            onPrimary = Color.Black
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Sidebar / Control Panel
                Column(
                    modifier = Modifier
                        .width(350.dp)
                        .fillMaxHeight()
                        .background(SurfaceGray)
                        .padding(24.dp)
                ) {
                    Text(
                        "WK POOL 2026",
                        style = MaterialTheme.typography.h5,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryGold
                    )
                    Text(
                        "Calculator & Analyzer",
                        style = MaterialTheme.typography.caption,
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    SectionHeader("CONFIG")

                    ModernButton(
                        text = "Select Participants",
                        icon = Icons.Default.Groups,
                        onClick = {
                            val chooser = JFileChooser().apply {
                                isMultiSelectionEnabled = true
                                fileFilter = FileNameExtensionFilter("Excel files", "xlsx")
                            }
                            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                                participantFiles = chooser.selectedFiles.toList()
                            }
                        }
                    )
                    Text("${participantFiles.size} files loaded", color = Color.LightGray, fontSize = 12.sp)

                    Spacer(modifier = Modifier.height(16.dp))

                    ModernButton(
                        text = "Set Ground Truth",
                        icon = Icons.Default.Analytics,
                        onClick = {
                            val chooser = JFileChooser().apply {
                                fileFilter = FileNameExtensionFilter("Excel files", "xlsx")
                            }
                            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                                resultsFile = chooser.selectedFile
                            }
                        }
                    )
                    Text(resultsFile?.name ?: "Using Mock Engine", color = Color.LightGray, fontSize = 12.sp)

                    Spacer(modifier = Modifier.weight(1f))

                    if (statusMessage.isNotEmpty()) {
                        Text(statusMessage, color = PrimaryGold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Button(
                        onClick = {
                            isLoading = true
                            statusMessage = "Processing..."
                            Thread {
                                try {
                                    val structureFile = File("WK-pool.xlsx")
                                    val (groups, structureMatches) = reader.readTournamentStructure(structureFile.inputStream())

                                    val resultProvider: ResultProvider = if (resultsFile != null) {
                                        ExcelResultProvider(resultsFile!!.inputStream(), structureMatches)
                                    } else {
                                        MockResultProvider(structureMatches)
                                    }

                                    val rawResults = resultProvider.getResults()
                                    val actualTournament = tournamentLogic.simulateTournament(structureMatches, groups, rawResults)

                                    val results = participantFiles.map { file ->
                                        val p = reader.readParticipant(file.inputStream(), structureMatches)
                                        val pTournament = tournamentLogic.simulateTournament(structureMatches, groups, p.predictions)
                                        p.name to scoringEngine.calculateScoreBreakdown(Participant(p.name, pTournament), actualTournament)
                                    }.sortedByDescending { it.second.totalScore }

                                    rankings = results
                                    statusMessage = "Analysis Ready"
                                } catch (e: Exception) {
                                    statusMessage = "Error: ${e.message}"
                                } finally {
                                    isLoading = false
                                }
                            }.start()
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(8.dp),
                        enabled = participantFiles.isNotEmpty() && !isLoading
                    ) {
                        if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black)
                        else Text("CALCULATE RANKINGS", fontWeight = FontWeight.Bold)
                    }
                }

                // Main Content Area
                Column(modifier = Modifier.weight(1f).padding(24.dp)) {
                    if (selectedParticipant == null) {
                        Text("Rankings Dashboard", style = MaterialTheme.typography.h4, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(24.dp))

                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(rankings) { item ->
                                RankingCard(item) { selectedParticipant = item }
                            }
                        }
                    } else {
                        // Detailed View
                        val (name, breakdown) = selectedParticipant!!
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { selectedParticipant = null }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = PrimaryGold)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("$name's Detailed Analysis", style = MaterialTheme.typography.h4, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(modifier = Modifier.fillMaxSize()) {
                            // Match Results
                            Column(modifier = Modifier.weight(1f)) {
                                SectionHeader("MATCH-BY-MATCH PERFORMANCE")
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(breakdown.matchScores) { info ->
                                        MatchScoreRow(info)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(24.dp))

                            // Advancement
                            Column(modifier = Modifier.width(300.dp)) {
                                SectionHeader("TOURNAMENT PROGRESSION")
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(breakdown.advancementScores) { info ->
                                        AdvancementRow(info)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.overline,
        color = Color.Gray,
        letterSpacing = 2.sp,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

@Composable
fun ModernButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text)
    }
}

@Composable
fun RankingCard(item: Pair<String, ScoreBreakdown>, onClick: () -> Unit) {
    val (name, breakdown) = item
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        elevation = 4.dp,
        backgroundColor = SurfaceGray
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = PrimaryGold
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(name.take(1).uppercase(), color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(name, style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
                    Text("${breakdown.matchScores.size} matches predicted", style = MaterialTheme.typography.caption, color = Color.Gray)
                }
            }
            Text("${breakdown.totalScore} pts", style = MaterialTheme.typography.h5, fontWeight = FontWeight.Bold, color = PrimaryGold)
        }
    }
}

@Composable
fun MatchScoreRow(info: MatchScoreInfo) {
    Card(backgroundColor = DarkGray, shape = RoundedCornerShape(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "M${info.match.id}",
                modifier = Modifier.width(40.dp),
                style = MaterialTheme.typography.caption,
                color = Color.Gray
            )
            Text(
                "${info.match.team1?.name} vs ${info.match.team2?.name}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.body2
            )
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(100.dp)) {
                Text("Pred: ${info.predictedGoals1 ?: "-"}-${info.predictedGoals2 ?: "-"}", fontSize = 10.sp, color = Color.LightGray)
                Text("Real: ${info.match.goals1 ?: "-"}-${info.match.goals2 ?: "-"}", fontSize = 10.sp, color = PrimaryGold)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Surface(
                color = if (info.points > 0) SuccessGreen.copy(alpha = 0.2f) else Color.Transparent,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    "+${info.points}",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = if (info.points > 0) SuccessGreen else Color.Gray,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun AdvancementRow(info: AdvancementScoreInfo) {
    Card(backgroundColor = DarkGray, shape = RoundedCornerShape(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(info.team.name, style = MaterialTheme.typography.body2, fontWeight = FontWeight.Bold)
                Text(info.round.name.replace("_", " "), style = MaterialTheme.typography.caption, color = Color.Gray)
            }
            Text("+${info.points}", color = PrimaryGold, fontWeight = FontWeight.Bold)
        }
    }
}
