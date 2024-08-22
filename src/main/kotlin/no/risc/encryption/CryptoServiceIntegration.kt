package no.risc.encryption

import no.risc.exception.exceptions.SopsEncryptionException
import no.risc.infra.connector.CryptoServiceConnector
import no.risc.infra.connector.models.GCPAccessToken
import org.slf4j.LoggerFactory
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.util.UriBuilder

data class EncryptionRequest(
    val text: String,
    val config: String,
    val gcpAccessToken: String,
    val riScId: String,
)

@Component
class CryptoServiceIntegration(
    private val cryptoServiceConnector: CryptoServiceConnector,
) {
    private val logger = LoggerFactory.getLogger(CryptoServiceIntegration::class.java)

    fun encrypt(
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
                riScId = riScId,
            )
        }
    }

    fun decrypt(
        ciphertext: String,
        gcpAccessToken: GCPAccessToken,
    ): String {
        try {
            val encryptedFile =
                cryptoServiceConnector.webClient.post()
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
