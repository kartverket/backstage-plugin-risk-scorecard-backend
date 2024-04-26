package no.risc.infra.connector


import org.springframework.stereotype.Component

@Component
class GoogleApiConnector(
    baseUrl: String = "https://oauth2.googleapis.com/tokeninfo"
) : WebClientConnector(baseUrl) {
    fun validateAccessToken(token: String): Boolean {
        return try {
            val response = webClient.get()
                .uri("?access_token= $token")
                .retrieve()
                .bodyToMono(String::class.java)
                .block()

            // If the response contains the word "email", the token is valid
            response?.contains("email") ?: false

        } catch (e: Exception) {
            false
        }
    }
}