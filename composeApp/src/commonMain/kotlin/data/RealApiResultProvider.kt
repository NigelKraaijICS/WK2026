package data

import model.Match

/**
 * Implementation of [ResultProvider] that fetches data from a real football API.
 *
 * To switch to a real API:
 * 1. Choose an API provider (e.g., https://www.football-data.org/ or https://api-football.com/).
 * 2. Add Ktor-client and Serialization dependencies to build.gradle.kts.
 * 3. Implement the [getResults] method using Ktor to fetch the JSON.
 * 4. Map the API's JSON response to our [Match] model.
 */
class RealApiResultProvider : ResultProvider {
    override fun getResults(): List<Match> {
        // Example implementation sketch:
        // val client = HttpClient()
        // val response: HttpResponse = client.get("https://api.football-data.org/v4/competitions/WC/matches")
        // val apiMatches = response.body<List<ApiMatch>>()
        // return apiMatches.map { it.toDomainMatch() }

        return emptyList()
    }
}
