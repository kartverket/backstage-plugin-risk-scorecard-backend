package no.risc.infra.connector

import org.springframework.stereotype.Component

@Component
class GoogleOAuthApiConnector(
    baseUrl: String = "https://oauth2.googleapis.com/tokeninfo",
) : WebClientConnector(baseUrl) {
    fun validateAccessToken(token: String): Boolean = fetchTokenInfo(token) != null

    private fun fetchTokenInfo(token: String): String? =
        try {
            webClient
                .get()
                .uri("?access_token=$token")
                .retrieve()
                .bodyToMono(String::class.java)
                .block()
        } catch (e: Exception) {
            throw Exception("Invalid access token: $e")
        }
}
