package no.risc.encryption

import no.risc.exception.exceptions.SOPSDecryptionException
import no.risc.exception.exceptions.SopsEncryptionException
import no.risc.infra.connector.CryptoServiceConnector
import no.risc.infra.connector.models.GCPAccessToken
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.awaitBody

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
    companion object {
        val LOGGER: Logger = LoggerFactory.getLogger(CryptoServiceIntegration::class.java)
    }

    fun encrypt(
        text: String,
        config: String,
        gcpAccessToken: GCPAccessToken,
        riScId: String,
    ): String {
        val encryptionRequest =
            EncryptionRequest(text = text, config = config, gcpAccessToken = gcpAccessToken.value, riScId = riScId)

        return try {
            cryptoServiceConnector.webClient
                .post()
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

    suspend fun decrypt(
        ciphertext: String,
        gcpAccessToken: GCPAccessToken,
    ): String =
        try {
            LOGGER.info("Trying to decrypt ciphertext: ${ciphertext.substring(0, 14)}")
            val decryptedFile =
                cryptoServiceConnector.webClient
                    .post()
                    .uri("/decrypt")
                    .header("gcpAccessToken", gcpAccessToken.value)
                    .bodyValue(ciphertext)
                    .retrieve()
                    .awaitBody<String>()
            LOGGER.info(
                "Successfully decrypted ciphertext ${
                    ciphertext.substring(
                        0,
                        14,
                    )
                } to ${decryptedFile.substring(3, 10)}",
            )
            decryptedFile
        } catch (e: Exception) {
            throw (SOPSDecryptionException(message = "Failed to decrypt file"))
        }
}
