package no.risc.encryption

import no.risc.encryption.models.EncryptionRequest
import no.risc.exception.exceptions.SOPSDecryptionException
import no.risc.exception.exceptions.SopsEncryptionException
import no.risc.infra.connector.CryptoServiceConnector
import no.risc.infra.connector.models.GCPAccessToken
import no.risc.risc.models.RiScWithConfig
import no.risc.risc.models.SopsConfig
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.awaitBody
import kotlin.math.min

@Component
class CryptoServiceIntegration(
    private val cryptoServiceConnector: CryptoServiceConnector,
) {
    companion object {
        val LOGGER: Logger = LoggerFactory.getLogger(CryptoServiceIntegration::class.java)
    }

    suspend fun encrypt(
        text: String,
        sopsConfig: SopsConfig,
        gcpAccessToken: GCPAccessToken,
        riScId: String,
    ): String =
        try {
            LOGGER.info("Trying to encrypt text: ${text.substring(0, min(text.length, 20))}...")
            cryptoServiceConnector.webClient
                .post()
                .uri("/encrypt")
                .body(
                    BodyInserters.fromValue(
                        EncryptionRequest(
                            text = text,
                            config = sopsConfig,
                            gcpAccessToken = gcpAccessToken.value,
                            riScId = riScId,
                        ),
                    ),
                ).retrieve()
                .awaitBody<String>()
                .also {
                    LOGGER.info(
                        "Successfully encrypted text ${text.substring(0, min(text.length, 20))}..." +
                            "to ${it.substring(0, min(it.length, 20))}...",
                    )
                }
        } catch (e: NoSuchElementException) {
            throw SopsEncryptionException(
                message = "Failed to encrypt RiSc, no response body received from encryption service.",
                riScId = riScId,
            )
        } catch (e: Exception) {
            throw SopsEncryptionException(message = e.stackTraceToString(), riScId = riScId)
        }

    suspend fun decrypt(
        ciphertext: String,
        gcpAccessToken: GCPAccessToken,
    ): RiScWithConfig =
        try {
            LOGGER.info("Trying to decrypt ciphertext: ${ciphertext.substring(0, min(ciphertext.length, 20))}...")
            cryptoServiceConnector.webClient
                .post()
                .uri("/decrypt")
                .header("gcpAccessToken", gcpAccessToken.value)
                .bodyValue(ciphertext)
                .retrieve()
                .awaitBody<RiScWithConfig>()
                .also {
                    LOGGER.info(
                        "Successfully decrypted ciphertext ${ciphertext.substring(0, min(ciphertext.length, 20))}..." +
                            "to ${it.riSc.substring(0, min(it.riSc.length, 20))}...",
                    )
                }
        } catch (_: NoSuchElementException) {
            throw SOPSDecryptionException(message = "Failed to decrypt ciphertext, no response body received from decryption service.")
        } catch (e: Exception) {
            throw SOPSDecryptionException(message = e.stackTraceToString())
        }
}
