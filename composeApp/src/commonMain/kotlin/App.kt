import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
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
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private val DarkGray = Color(0xFF0A0A0A)
private val SurfaceGray = Color(0xFF161616)
private val CardGray = Color(0xFF222222)
private val PrimaryGold = Color(0xFFFFD700)
private val SuccessGreen = Color(0xFF00E676)
private val ErrorRed = Color(0xFFFF5252)

enum class ResultSource { EXCEL, MOCK, API }
enum class ViewMode { OVERALL, ROUND_ANALYSIS, MATCH_ANALYSIS }

@Composable
fun App() {
    var participantFiles by remember { mutableStateOf(listOf<File>()) }
    var resultsFile by remember { mutableStateOf<File?>(null) }
    var resultSource by remember { mutableStateOf(ResultSource.MOCK) }
    var rankings by remember { mutableStateOf(listOf<Pair<String, ScoreBreakdown>>()) }
    var selectedParticipant by remember { mutableStateOf<Pair<String, ScoreBreakdown>?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var currentViewMode by remember { mutableStateOf(ViewMode.OVERALL) }
    var selectedRoundFilter by remember { mutableStateOf<Round?>(null) }
    var selectedMatchId by remember { mutableStateOf<Int?>(null) }
    var tournamentStructure by remember { mutableStateOf<List<Match>>(emptyList()) }
    var onlyCalculatePastGames by remember { mutableStateOf(false) }

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
                // Sidebar
                Column(
                    modifier = Modifier
                        .width(340.dp)
                        .fillMaxHeight()
                        .background(SurfaceGray)
                        .padding(24.dp)
                ) {
                    Text("WK POOL 2026", style = MaterialTheme.typography.h5, fontWeight = FontWeight.Black, color = PrimaryGold)
                    Text("Professional Analytics Dashboard", style = MaterialTheme.typography.caption, color = Color.Gray)

                    Spacer(modifier = Modifier.height(32.dp))

                    SectionHeader("VIEW MODE")
                    NavButton("Overall Rankings", Icons.Default.EmojiEvents, currentViewMode == ViewMode.OVERALL) { currentViewMode = ViewMode.OVERALL }
                    NavButton("Round Analysis", Icons.Default.Category, currentViewMode == ViewMode.ROUND_ANALYSIS) { currentViewMode = ViewMode.ROUND_ANALYSIS }
                    NavButton("Match Analytics", Icons.Default.SportsSoccer, currentViewMode == ViewMode.MATCH_ANALYSIS) { currentViewMode = ViewMode.MATCH_ANALYSIS }

                    Spacer(modifier = Modifier.height(32.dp))
                    SectionHeader("SOURCE CONFIG")

                    ResultSourceOption("Master Excel", resultSource == ResultSource.EXCEL) { resultSource = ResultSource.EXCEL }
                    if (resultSource == ResultSource.EXCEL) {
                        ModernButton(text = resultsFile?.name ?: "Select Results", icon = Icons.Default.FileUpload) {
                            val chooser = JFileChooser().apply { fileFilter = FileNameExtensionFilter("Excel files", "xlsx") }
                            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) resultsFile = chooser.selectedFile
                        }
                    }
                    ResultSourceOption("Mock Data", resultSource == ResultSource.MOCK) { resultSource = ResultSource.MOCK }
                    ResultSourceOption("Live API", resultSource == ResultSource.API) { resultSource = ResultSource.API }

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = onlyCalculatePastGames, onCheckedChange = { onlyCalculatePastGames = it }, colors = CheckboxDefaults.colors(checkedColor = PrimaryGold))
                        Text("Only calculate past games", color = Color.LightGray, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    SectionHeader("PARTICIPANTS")
                    ModernButton("Load Pool Files (${participantFiles.size})", Icons.Default.Groups) {
                        val chooser = JFileChooser().apply { isMultiSelectionEnabled = true; fileFilter = FileNameExtensionFilter("Excel files", "xlsx") }
                        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) participantFiles = chooser.selectedFiles.toList()
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    if (statusMessage.isNotEmpty()) {
                        Text(statusMessage, color = if (statusMessage.contains("Error")) ErrorRed else PrimaryGold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Button(
                        onClick = {
                            isLoading = true
                            statusMessage = "Processing Analytics..."
                            Thread {
                                try {
                                    val structureFile = File("WK-pool.xlsx")
                                    val (groups, structureMatches) = reader.readTournamentStructure(structureFile.inputStream())
                                    tournamentStructure = structureMatches

                                    val resultProvider: ResultProvider = when (resultSource) {
                                        ResultSource.EXCEL -> ExcelResultProvider(resultsFile?.inputStream() ?: throw Exception("No result file selected"), structureMatches)
                                        ResultSource.MOCK -> MockResultProvider(structureMatches)
                                        ResultSource.API -> RealApiResultProvider(structureMatches)
                                    }

                                    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                                    var rawResults = resultProvider.getResults()

                                    if (onlyCalculatePastGames) {
                                        rawResults = rawResults.map { m ->
                                            if (m.date != null && m.date > now) m.copy(goals1 = null, goals2 = null) else m
                                        }
                                    }

                                    val actualTournament = tournamentLogic.simulateTournament(structureMatches, groups, rawResults)

                                    val results = participantFiles.map { file ->
                                        val name = file.nameWithoutExtension
                                        val p = reader.readParticipant(file.inputStream(), structureMatches, name)
                                        val pTournament = tournamentLogic.simulateTournament(structureMatches, groups, p.predictions)
                                        p.name to scoringEngine.calculateScoreBreakdown(Participant(p.name, pTournament), actualTournament)
                                    }.sortedByDescending { it.second.totalScore }

                                    rankings = results
                                    statusMessage = "Analysis Ready"
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    statusMessage = "Error: ${e.message}"
                                } finally {
                                    isLoading = false
                                }
                            }.start()
                        },
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                        shape = RoundedCornerShape(16.dp),
                        enabled = participantFiles.isNotEmpty() && !isLoading
                    ) {
                        if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black, strokeWidth = 3.dp)
                        else Text("RUN ENGINE", fontWeight = FontWeight.Bold)
                    }
                }

                // Main Content
                Column(modifier = Modifier.weight(1f).padding(32.dp)) {
                    if (selectedParticipant != null) {
                        ParticipantDetailView(selectedParticipant!!) { selectedParticipant = null }
                    } else {
                        when (currentViewMode) {
                            ViewMode.OVERALL -> OverallLeaderboard(rankings) { selectedParticipant = it }
                            ViewMode.ROUND_ANALYSIS -> RoundLeaderboard(rankings, selectedRoundFilter) { selectedRoundFilter = it }
                            ViewMode.MATCH_ANALYSIS -> MatchLeaderboard(rankings, tournamentStructure, selectedMatchId) { selectedMatchId = it }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NavButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(8.dp)).clickable { onClick() },
        color = if (selected) PrimaryGold.copy(alpha = 0.1f) else Color.Transparent
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = if (selected) PrimaryGold else Color.Gray, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(text, color = if (selected) PrimaryGold else Color.Gray, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

@Composable
fun OverallLeaderboard(rankings: List<Pair<String, ScoreBreakdown>>, onSelect: (Pair<String, ScoreBreakdown>) -> Unit) {
    Text("World Cup Leaderboard", style = MaterialTheme.typography.h3, fontWeight = FontWeight.Black)
    Spacer(modifier = Modifier.height(32.dp))
    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        items(rankings) { item -> RankingCard(item, onSelect) }
    }
}

@Composable
fun RoundLeaderboard(rankings: List<Pair<String, ScoreBreakdown>>, selectedRound: Round?, onRoundSelect: (Round) -> Unit) {
    Text("Round Analysis", style = MaterialTheme.typography.h3, fontWeight = FontWeight.Black)
    Spacer(modifier = Modifier.height(24.dp))

    ScrollableTabRow(selectedTabIndex = selectedRound?.ordinal ?: 0, backgroundColor = Color.Transparent, contentColor = PrimaryGold, edgePadding = 0.dp) {
        Round.values().forEach { round ->
            Tab(selected = selectedRound == round, onClick = { onRoundSelect(round) }) {
                Text(round.displayName.uppercase(), modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold, fontSize = 10.sp)
            }
        }
    }

    if (selectedRound != null) {
        val roundRankings = rankings.map { (name, breakdown) ->
            name to (breakdown.roundSummaries[selectedRound] ?: 0)
        }.sortedByDescending { it.second }

        Spacer(modifier = Modifier.height(24.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(roundRankings) { (name, score) ->
                SimpleRankingCard(name, score, "points in ${selectedRound.displayName}")
            }
        }
    }
}

@Composable
fun MatchLeaderboard(rankings: List<Pair<String, ScoreBreakdown>>, structure: List<Match>, selectedMatchId: Int?, onMatchSelect: (Int) -> Unit) {
    Text("Match Analytics", style = MaterialTheme.typography.h3, fontWeight = FontWeight.Black)
    Spacer(modifier = Modifier.height(24.dp))

    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            val m = structure.find { it.id == selectedMatchId }
            Text(if (m == null) "Select a Match" else "M${m.id}: ${m.team1?.name} vs ${m.team2?.name}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            structure.filter { it.round != Round.CHAMPION }.forEach { m ->
                DropdownMenuItem(onClick = { onMatchSelect(m.id); expanded = false }) {
                    Text("M${m.id}: ${m.team1?.name} vs ${m.team2?.name} (${m.round.displayName})")
                }
            }
        }
    }

    if (selectedMatchId != null) {
        val matchRankings = rankings.mapNotNull { (name, breakdown) ->
            val matchInfo = breakdown.matchScores.find { it.match.id == selectedMatchId }
            if (matchInfo != null) name to matchInfo else null
        }.sortedByDescending { it.second.points }

        Spacer(modifier = Modifier.height(24.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(matchRankings) { (name, info) ->
                DetailedMatchRankingCard(name, info)
            }
        }
    }
}

@Composable
fun ParticipantDetailView(item: Pair<String, ScoreBreakdown>, onBack: () -> Unit) {
    val (name, breakdown) = item
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = PrimaryGold) }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(name, style = MaterialTheme.typography.h4, fontWeight = FontWeight.Black)
            Text("Consolidated Score: ${breakdown.totalScore} pts", color = PrimaryGold, fontWeight = FontWeight.Bold)
        }
    }
    Spacer(modifier = Modifier.height(32.dp))
    Row(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.weight(1.2f)) {
            SectionHeader("MATCH ANALYTICS")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(breakdown.matchScores) { info -> DetailedMatchCard(info) }
            }
        }
        Spacer(modifier = Modifier.width(32.dp))
        Column(modifier = Modifier.weight(0.8f)) {
            SectionHeader("PROGRESSION BONUSES")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(breakdown.advancementScores) { info -> AdvancementBonusCard(info) }
            }
        }
    }
}

@Composable
fun SimpleRankingCard(name: String, score: Int, context: String) {
    Card(backgroundColor = CardGray, shape = RoundedCornerShape(12.dp)) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(name, fontWeight = FontWeight.Bold)
                Text(context, style = MaterialTheme.typography.caption, color = Color.Gray)
            }
            Text("$score", style = MaterialTheme.typography.h5, fontWeight = FontWeight.Black, color = PrimaryGold)
        }
    }
}

@Composable
fun DetailedMatchRankingCard(name: String, info: MatchScoreInfo) {
    Card(backgroundColor = CardGray, shape = RoundedCornerShape(12.dp)) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.Bold)
                Text("Pred: ${info.predictedGoals1}-${info.predictedGoals2} | ${info.explanation}", style = MaterialTheme.typography.caption, color = Color.Gray)
            }
            Text("+${info.points}", style = MaterialTheme.typography.h5, fontWeight = FontWeight.Black, color = if (info.points > 0) SuccessGreen else Color.Gray)
        }
    }
}

@Composable
fun ResultSourceOption(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick, colors = RadioButtonDefaults.colors(selectedColor = PrimaryGold))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, color = if (selected) Color.White else Color.Gray, fontSize = 14.sp)
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.overline, color = Color.Gray, letterSpacing = 2.sp, modifier = Modifier.padding(bottom = 12.dp))
}

@Composable
fun ModernButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(text, maxLines = 1, fontSize = 12.sp)
    }
}

@Composable
fun RankingCard(item: Pair<String, ScoreBreakdown>, onClick: (Pair<String, ScoreBreakdown>) -> Unit) {
    val (name, breakdown) = item
    Card(modifier = Modifier.fillMaxWidth().clickable { onClick(item) }, shape = RoundedCornerShape(20.dp), backgroundColor = CardGray) {
        Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(56.dp), shape = RoundedCornerShape(16.dp), color = PrimaryGold) {
                    Box(contentAlignment = Alignment.Center) { Text(name.take(1).uppercase(), color = Color.Black, style = MaterialTheme.typography.h5, fontWeight = FontWeight.Black) }
                }
                Spacer(modifier = Modifier.width(20.dp))
                Column {
                    Text(name, style = MaterialTheme.typography.h6, fontWeight = FontWeight.Bold)
                    Text("${breakdown.totalScore} pts", style = MaterialTheme.typography.caption, color = Color.Gray)
                }
            }
            Text("${breakdown.totalScore}", style = MaterialTheme.typography.h3, fontWeight = FontWeight.Black, color = PrimaryGold)
        }
    }
}

@Composable
fun DetailedMatchCard(info: MatchScoreInfo) {
    Card(backgroundColor = SurfaceGray, shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row {
                Text("MATCH ${info.match.id}", color = PrimaryGold, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                Spacer(modifier = Modifier.weight(1f))
                Text(info.match.round.displayName.uppercase(), style = MaterialTheme.typography.overline, color = Color.Gray)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(info.match.team1?.name ?: "TBD", fontWeight = FontWeight.Bold)
                    Text(info.match.team2?.name ?: "TBD", fontWeight = FontWeight.Bold)
                }
                ScoreBlock("REAL", info.match.goals1, info.match.goals2, PrimaryGold)
                Spacer(modifier = Modifier.width(20.dp))
                ScoreBlock("PRED", info.predictedGoals1, info.predictedGoals2, Color.White)
                Spacer(modifier = Modifier.width(32.dp))
                Text("+${info.points}", color = if (info.points > 0) SuccessGreen else Color.Gray, fontWeight = FontWeight.Black, fontSize = 20.sp)
            }
            Divider(modifier = Modifier.padding(vertical = 12.dp), color = Color.DarkGray)
            Text(info.explanation, style = MaterialTheme.typography.caption, color = Color.LightGray)
        }
    }
}

@Composable
fun ScoreBlock(label: String, s1: Int?, s2: Int?, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.overline, color = Color.Gray, fontSize = 8.sp)
        Text("${s1 ?: "-"}", color = color, fontWeight = FontWeight.Black, fontSize = 16.sp)
        Text("${s2 ?: "-"}", color = color, fontWeight = FontWeight.Black, fontSize = 16.sp)
    }
}

@Composable
fun AdvancementBonusCard(info: AdvancementScoreInfo) {
    Card(backgroundColor = SurfaceGray, shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(info.team.name, style = MaterialTheme.typography.h6, fontWeight = FontWeight.Black)
                    Text(info.round.displayName.uppercase(), color = PrimaryGold, style = MaterialTheme.typography.overline)
                }
                Text("+${info.points}", color = SuccessGreen, fontWeight = FontWeight.Black, fontSize = 22.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(info.explanation, style = MaterialTheme.typography.caption, color = Color.Gray)
        }
    }
}
