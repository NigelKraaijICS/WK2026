package data

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import model.Match
import model.Round
import model.Team

class RealApiResultProvider(private val structure: List<Match>) : ResultProvider {

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
            })
        }
    }

    override fun getResults(): List<Match> {
        // Since we are in a common module and need to return results synchronously for the current architecture,
        // we'll use runBlocking or similar if needed, but the UI calls this in a thread anyway.
        return kotlinx.coroutines.runBlocking {
            try {
                val response: ApiResponse = client.get("https://worldcup26.ir/get/games").body()
                response.games.mapNotNull { game ->
                    val id = game.id.toIntOrNull() ?: return@mapNotNull null
                    val matchStruct = structure.find { it.id == id }

                    Match(
                        id = id,
                        team1Placeholder = matchStruct?.team1Placeholder ?: "",
                        team2Placeholder = matchStruct?.team2Placeholder ?: "",
                        team1 = Team(game.home_team_name_en),
                        team2 = Team(game.away_team_name_en),
                        goals1 = game.home_score.toIntOrNull(),
                        goals2 = game.away_score.toIntOrNull(),
                        round = matchStruct?.round ?: Round.GROUP
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }
}

@Serializable
data class ApiResponse(val games: List<ApiGame>)

@Serializable
data class ApiGame(
    val id: String,
    val home_score: String,
    val away_score: String,
    val home_team_name_en: String,
    val away_team_name_en: String
)
