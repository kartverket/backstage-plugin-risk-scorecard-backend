package no.risc.infra.connector

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.springframework.stereotype.Component

@Component
class GoogleApiConnector(
    baseUrl: String = "https://oauth2.googleapis.com/tokeninfo",
) : WebClientConnector(baseUrl) {
    fun validateAccessToken(token: String): Boolean = fetchTokenInfo(token) != null

    fun fetchTokenInfo(token: String): String? {
        return try {
            webClient.get()
                .uri("?access_token=$token")
                .retrieve()
                .bodyToMono(String::class.java)
                .block()
        } catch (e: Exception) {
            throw Exception("Invalid access token: $e")
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class UserInfo(
    val name: String,
    val email: String,
)
