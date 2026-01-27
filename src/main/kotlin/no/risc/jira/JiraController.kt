package no.risc.jira

import com.google.gson.Gson
import org.json.JSONArray
import org.json.JSONObject
import org.springframework.beans.factory.annotation.Value

import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import kotlin.collections.mapOf
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@RestController
@RequestMapping("/api/jira")
class JiraController {
    @Value("\${jira.projectKey:KAN}")
    private lateinit var projectKey: String
    private val gson = Gson()

    private val httpClient = HttpClient.newHttpClient()
    private var cachedCloudId: String? = null

    @GetMapping("/cloudId")
    private fun getCloudId(token: String): String {
        if (cachedCloudId != null) {
            return cachedCloudId!!
        }
        val request =
            HttpRequest
                .newBuilder()
                .uri(URI.create("https://api.atlassian.com/oauth/token/accessible-resources"))
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .GET()
                .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        val jsonArray = JSONArray(response.body())
        val cloudId = jsonArray.getJSONObject(0).getString("id")

        cachedCloudId = cloudId
        return cloudId
    }

    @PostMapping("/issue/create")
    fun createIssue(
        @RequestHeader(HttpHeaders.AUTHORIZATION) authHeader: String,
        @RequestBody issueData: Map<String, Any>,
    ): ResponseEntity<out Any?>? {
        val token = authHeader.replace("Bearer ", "")

        try {
            val cloudId = getCloudId(token)
            val jiraDomain = System.getenv("JIRA_DOMAIN") ?: "spirekartverket.atlassian.net"


            val body: Map<String, Any> = mapOf(
                "fields" to mapOf<String, Any>(
                    "project" to mapOf<String, String>("key" to projectKey),
                    "summary" to (issueData["title"] as? String ?: ""),
                    "description" to mapOf<String, Any>(
                        "version" to 1,
                        "type" to "doc",
                        "content" to listOf(
                            mapOf(
                                "type" to "paragraph",
                                "content" to listOf(
                                    mapOf(
                                        "type" to "text",
                                        "text" to (issueData["description"] as? String ?: "")
                                    )
                                )
                            )
                        )
                    ),
                    "issuetype" to mapOf<String, String>("name" to (issueData["issueType"] as? String ?: "Task"))
                )
            )

            println("Request body: ${gson.toJson(body)}")

            val request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.atlassian.com/ex/jira/$cloudId/rest/api/3/issue"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            val createdIssue = gson.fromJson(response.body(), Map::class.java)
            val issueKey = createdIssue["key"] as String

            val url = "https://$jiraDomain/browse/$issueKey/"

            return ResponseEntity.ok(mapOf(
                "key" to issueKey,
                "url" to url,
                "message" to "Issue created successfully",
            ))

        } catch (e: Exception) {
            return ResponseEntity.status(500).body("Error: ${e.message}")
        }

    }

    @DeleteMapping("/issue/{issueKey}")
    fun deleteIssue(
        @PathVariable issueKey: String,
        @RequestHeader(HttpHeaders.AUTHORIZATION) authHeader: String

    ): ResponseEntity<Map<String, Any>>{
        val token = authHeader.replace("Bearer ", "")
        try {

            val cloudId = getCloudId(token)

            val request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.atlassian.com/ex/jira/$cloudId/rest/api/3/issue/$issueKey"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .DELETE()
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            return if (response.statusCode() == 204) {
                ResponseEntity.ok(mapOf(
                    "message" to "Issue deleted successfully",
                    "key" to issueKey
                ))
            } else {
                ResponseEntity.status(500).body(mapOf(
                    "error" to response.body()
                ))
            }
        } catch (e: Exception){
            println("Delete error: ${e.message}")
            e.printStackTrace()
            return ResponseEntity.status(500).body(mapOf(
                "error" to (e.message ?: "Unknown error")
            ))
        }
    }

}
