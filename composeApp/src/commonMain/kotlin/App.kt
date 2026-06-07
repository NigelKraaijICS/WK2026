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

private val DarkGray = Color(0xFF0A0A0A)
private val SurfaceGray = Color(0xFF161616)
private val CardGray = Color(0xFF222222)
private val PrimaryGold = Color(0xFFFFD700)
private val SuccessGreen = Color(0xFF00E676)
private val ErrorRed = Color(0xFFFF5252)

enum class ResultSource { EXCEL, MOCK, API }

@Composable
fun App() {
    var participantFiles by remember { mutableStateOf(listOf<File>()) }
    var resultsFile by remember { mutableStateOf<File?>(null) }
    var resultSource by remember { mutableStateOf(ResultSource.MOCK) }
    var rankings by remember { mutableStateOf(listOf<Pair<String, ScoreBreakdown>>()) }
    var selectedParticipant by remember { mutableStateOf<Pair<String, ScoreBreakdown>?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    var selectedRoundFilter by remember { mutableStateOf<Round?>(null) }

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

                    Spacer(modifier = Modifier.height(40.dp))

                    SectionHeader("SOURCE CONFIGURATION")

                    ResultSourceOption(
                        title = "Master Excel",
                        subtitle = "Use a filled-in WK-pool.xlsx",
                        selected = resultSource == ResultSource.EXCEL,
                        onClick = { resultSource = ResultSource.EXCEL }
                    )

                    if (resultSource == ResultSource.EXCEL) {
                        Spacer(modifier = Modifier.height(8.dp))
                        ModernButton(
                            text = if (resultsFile == null) "Select Result File" else resultsFile!!.name,
                            icon = Icons.Default.FileUpload,
                            onClick = {
                                val chooser = JFileChooser().apply { fileFilter = FileNameExtensionFilter("Excel files", "xlsx") }
                                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) resultsFile = chooser.selectedFile
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    ResultSourceOption(
                        title = "Mock Results",
                        subtitle = "Simulated data for testing",
                        selected = resultSource == ResultSource.MOCK,
                        onClick = { resultSource = ResultSource.MOCK }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    ResultSourceOption(
                        title = "Live API",
                        subtitle = "Real-time data from worldcup26.ir",
                        selected = resultSource == ResultSource.API,
                        onClick = { resultSource = ResultSource.API }
                    )

                    Spacer(modifier = Modifier.height(32.dp))
                    SectionHeader("PARTICIPANTS")

                    ModernButton(
                        text = "Load Pool Files (${participantFiles.size})",
                        icon = Icons.Default.Groups,
                        onClick = {
                            val chooser = JFileChooser().apply {
                                isMultiSelectionEnabled = true
                                fileFilter = FileNameExtensionFilter("Excel files", "xlsx")
                            }
                            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) participantFiles = chooser.selectedFiles.toList()
                        }
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    if (statusMessage.isNotEmpty()) {
                        Text(statusMessage, color = if (statusMessage.contains("Error")) ErrorRed else PrimaryGold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Button(
                        onClick = {
                            isLoading = true
                            statusMessage = "Syncing & Calculating..."
                            Thread {
                                try {
                                    val structureFile = File("WK-pool.xlsx")
                                    val (groups, structureMatches) = reader.readTournamentStructure(structureFile.inputStream())

                                    val resultProvider: ResultProvider = when (resultSource) {
                                        ResultSource.EXCEL -> ExcelResultProvider(resultsFile?.inputStream() ?: throw Exception("No result file selected"), structureMatches)
                                        ResultSource.MOCK -> MockResultProvider(structureMatches)
                                        ResultSource.API -> RealApiResultProvider(structureMatches)
                                    }

                                    val rawResults = resultProvider.getResults()
                                    val actualTournament = tournamentLogic.simulateTournament(structureMatches, groups, rawResults)

                                    val results = participantFiles.map { file ->
                                        val p = reader.readParticipant(file.inputStream(), structureMatches)
                                        val pTournament = tournamentLogic.simulateTournament(structureMatches, groups, p.predictions)
                                        p.name to scoringEngine.calculateScoreBreakdown(Participant(p.name, pTournament), actualTournament)
                                    }.sortedByDescending { it.second.totalScore }

                                    rankings = results
                                    statusMessage = "Update Successful"
                                } catch (e: Exception) {
                                    statusMessage = "Error: ${e.message}"
                                } finally {
                                    isLoading = false
                                }
                            }.start()
                        },
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                        shape = RoundedCornerShape(16.dp),
                        enabled = participantFiles.isNotEmpty() && !isLoading && (resultSource != ResultSource.EXCEL || resultsFile != null)
                    ) {
                        if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black, strokeWidth = 3.dp)
                        else Text("EXECUTE ANALYSIS", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                }

                // Main Content
                Column(modifier = Modifier.weight(1f).padding(32.dp)) {
                    if (selectedParticipant == null) {
                        Text("World Cup Leaderboard", style = MaterialTheme.typography.h3, fontWeight = FontWeight.Black)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Showing rankings based on ${resultSource.name} source", color = Color.Gray)
                        Spacer(modifier = Modifier.height(32.dp))

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(bottom = 32.dp)
                        ) {
                            items(rankings) { item ->
                                RankingCard(item) { selectedParticipant = item }
                            }
                        }
                    } else {
                        // Detailed View
                        val (name, breakdown) = selectedParticipant!!
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { selectedParticipant = null; selectedRoundFilter = null }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = PrimaryGold)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(name, style = MaterialTheme.typography.h4, fontWeight = FontWeight.Black)
                                Text("Consolidated Score: ${breakdown.totalScore} pts", color = PrimaryGold, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        // Round Filter Tabs
                        ScrollableTabRow(
                            selectedTabIndex = (if (selectedRoundFilter == null) 0 else selectedRoundFilter!!.ordinal + 1),
                            backgroundColor = Color.Transparent,
                            contentColor = PrimaryGold,
                            edgePadding = 0.dp,
                            divider = {}
                        ) {
                            Tab(selected = selectedRoundFilter == null, onClick = { selectedRoundFilter = null }) {
                                Text("ALL ROUNDS", modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Round.values().forEach { round ->
                                val roundScore = breakdown.roundSummaries[round] ?: 0
                                Tab(selected = selectedRoundFilter == round, onClick = { selectedRoundFilter = round }) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(12.dp)) {
                                        Text(round.displayName.uppercase(), fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                        Text("$roundScore pts", fontSize = 10.sp, color = if (roundScore > 0) SuccessGreen else Color.Gray)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(modifier = Modifier.fillMaxSize()) {
                            // Match Performance
                            Column(modifier = Modifier.weight(1.2f)) {
                                SectionHeader("MATCH ANALYTICS")
                                val filteredMatches = if (selectedRoundFilter == null) breakdown.matchScores
                                                       else breakdown.matchScores.filter { it.match.round == selectedRoundFilter }

                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(bottom = 32.dp)
                                ) {
                                    items(filteredMatches) { info ->
                                        DetailedMatchCard(info)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(32.dp))

                            // Tournament Progress
                            Column(modifier = Modifier.weight(0.8f)) {
                                SectionHeader("PROGRESSION BONUSES")
                                val filteredAdv = if (selectedRoundFilter == null) breakdown.advancementScores
                                                   else breakdown.advancementScores.filter { it.round == selectedRoundFilter }

                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(bottom = 32.dp)
                                ) {
                                    items(filteredAdv) { info ->
                                        AdvancementBonusCard(info)
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
fun ResultSourceOption(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) PrimaryGold.copy(alpha = 0.1f) else Color.Transparent)
            .border(1.dp, if (selected) PrimaryGold else Color.DarkGray, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick, colors = RadioButtonDefaults.colors(selectedColor = PrimaryGold))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold, color = if (selected) PrimaryGold else Color.White)
            Text(subtitle, style = MaterialTheme.typography.caption, color = Color.Gray)
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
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.DarkGray)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(text, maxLines = 1)
    }
}

@Composable
fun RankingCard(item: Pair<String, ScoreBreakdown>, onClick: () -> Unit) {
    val (name, breakdown) = item
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        elevation = 0.dp,
        backgroundColor = CardGray
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = PrimaryGold
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(name.take(1).uppercase(), color = Color.Black, style = MaterialTheme.typography.h4, fontWeight = FontWeight.Black)
                    }
                }
                Spacer(modifier = Modifier.width(24.dp))
                Column {
                    Text(name, style = MaterialTheme.typography.h5, fontWeight = FontWeight.Bold)
                    Text("View detailed scorecard", style = MaterialTheme.typography.caption, color = Color.Gray)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${breakdown.totalScore}", style = MaterialTheme.typography.h3, fontWeight = FontWeight.Black, color = PrimaryGold)
                Text("TOTAL POINTS", style = MaterialTheme.typography.overline, color = PrimaryGold.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
fun DetailedMatchCard(info: MatchScoreInfo) {
    Card(backgroundColor = CardGray, shape = RoundedCornerShape(16.dp), elevation = 0.dp) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("MATCH ${info.match.id}", color = PrimaryGold, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.weight(1f))
                Text(info.match.round.displayName.uppercase(), style = MaterialTheme.typography.overline, color = Color.Gray)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    TeamRow(info.match.team1?.name ?: "TBD", isHome = true)
                    Spacer(modifier = Modifier.height(8.dp))
                    TeamRow(info.match.team2?.name ?: "TBD", isHome = false)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    ScoreBlock("REAL", info.match.goals1, info.match.goals2, PrimaryGold)
                    Spacer(modifier = Modifier.width(20.dp))
                    ScoreBlock("PRED", info.predictedGoals1, info.predictedGoals2, Color.White)
                }

                Spacer(modifier = Modifier.width(32.dp))

                Surface(
                    color = if (info.points > 0) SuccessGreen.copy(alpha = 0.12f) else Color.DarkGray,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "+${info.points}",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        color = if (info.points > 0) SuccessGreen else Color.Gray,
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp
                    )
                }
            }

            Divider(modifier = Modifier.padding(vertical = 16.dp), color = Color.DarkGray.copy(alpha = 0.5f))

            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(14.dp).padding(top = 2.dp), tint = Color.Gray)
                Spacer(modifier = Modifier.width(8.dp))
                Text(info.explanation, style = MaterialTheme.typography.caption, color = Color.LightGray)
            }
        }
    }
}

@Composable
fun TeamRow(name: String, isHome: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(4.dp).background(if (isHome) PrimaryGold else Color.Gray, RoundedCornerShape(2.dp)))
        Spacer(modifier = Modifier.width(12.dp))
        Text(name, style = MaterialTheme.typography.body1, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun ScoreBlock(label: String, s1: Int?, s2: Int?, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.overline, color = Color.Gray, fontSize = 8.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text("${s1 ?: "-"}", color = color, fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text("${s2 ?: "-"}", color = color, fontWeight = FontWeight.Black, fontSize = 18.sp)
    }
}

@Composable
fun AdvancementBonusCard(info: AdvancementScoreInfo) {
    Card(backgroundColor = CardGray, shape = RoundedCornerShape(16.dp), elevation = 0.dp) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(info.team.name, style = MaterialTheme.typography.h6, fontWeight = FontWeight.Black)
                    Text(info.round.displayName.uppercase(), style = MaterialTheme.typography.overline, color = PrimaryGold)
                }
                Text("+${info.points}", color = SuccessGreen, fontWeight = FontWeight.Black, fontSize = 22.sp)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(info.explanation, style = MaterialTheme.typography.caption, color = Color.Gray)
        }
    }
}
