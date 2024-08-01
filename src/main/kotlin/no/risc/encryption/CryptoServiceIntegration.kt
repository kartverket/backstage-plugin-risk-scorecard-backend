package no.risc.encryption

import no.risc.exception.exceptions.SopsEncryptionException
import no.risc.infra.connector.CryptoServiceConnector
import no.risc.infra.connector.models.GCPAccessToken
import org.slf4j.LoggerFactory
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
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
    private val logger = LoggerFactory.getLogger(CryptoServiceIntegration::class.java)

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
                .body(BodyInserters.fromValue(encryptionRequest))
                .retrieve()
                .bodyToMono(String::class.java)
                .block()
                .toString()
        } catch (e: Exception) {
            throw SopsEncryptionException(
                message = e.stackTraceToString(),
                riScId = riScId
            )
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
        try {
            val encryptedFile =
                cryptoServiceConnector.webClient.method(HttpMethod.GET)
                    .uri { uriBuilder: UriBuilder ->
                        uriBuilder.path("/decrypt")
                            .build()
                    }
                    .header("gcpAccessToken", gcpAccessToken.value)
                    .bodyValue(ciphertext)
                    .retrieve()
                    .bodyToMono(String::class.java)
                    .block()
                    .toString()

            return encryptedFile
        } catch (e: Exception) {
            logger.error("Decrypting failed!", e)
            return ""
        }
    }
}
