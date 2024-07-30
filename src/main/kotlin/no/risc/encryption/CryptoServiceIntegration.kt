package no.risc.encryption

import no.risc.infra.connector.CryptoServiceConnector
import no.risc.infra.connector.models.GCPAccessToken
import org.springframework.stereotype.Component
import org.springframework.web.util.UriBuilder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class EncryptionRequest(
    val text: String,
    val config: String,
    val gcpAccessToken: String,
    val riScId: String,
)

@Component
class CryptoServiceIntegration(
    private val cryptoServiceConnector: CryptoServiceConnector,
) : ISopsEncryption {
    fun encryptPost(
        text: String,
        config: String,
        gcpAccessToken: GCPAccessToken,
        riScId: String,
    ): String {
        val encryptionRequest =
            EncryptionRequest(text = text, config = config, gcpAccessToken = gcpAccessToken.value, riScId = riScId)

        return try {
            cryptoServiceConnector.webClient.post()
                .uri("/encrypt")
                .body(encryptionRequest, EncryptionRequest::class.java)
                .retrieve()
                .bodyToMono(String::class.java)
                .block()
                .toString()
        } catch (e: Exception) {
            "Exception caught: ${e.stackTraceToString()}"
        }
    }

    override fun encrypt(
        text: String,
        config: String,
        gcpAccessToken: GCPAccessToken,
        riScId: String,
    ): String {
        val urltext = URLEncoder.encode(text, StandardCharsets.UTF_8.toString())

        return try {
            cryptoServiceConnector.webClient.get()
                .uri { uriBuilder: UriBuilder ->
                    uriBuilder.path("/encrypt")
                        .queryParam("text", urltext)
                        .queryParam("config", config)
                        .queryParam("gcpAccessToken", gcpAccessToken.value)
                        .queryParam("riScId", riScId)
                        .build()
                }
                .retrieve()
                .bodyToMono(String::class.java)
                .block()
                .toString()
        } catch (e: Exception) {
            "Exception caught: ${e.stackTraceToString()}"
        }
    }

    override fun decrypt(
        ciphertext: String,
        gcpAccessToken: GCPAccessToken,
        agePrivateKey: String,
    ): String {
        TODO("Not yet implemented")
    }
}
