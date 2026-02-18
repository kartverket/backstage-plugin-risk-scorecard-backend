package no.risc.encryption

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
import org.springframework.web.reactive.function.client.bodyToMono
import kotlin.math.min

// Custom exception to carry crypto service error details
class CryptoServiceErrorException(
    message: String,
    val errorCode: String?,
    val errorMessage: String?,
) : RuntimeException(message)

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
                .onStatus({ status -> status.isError }) { response ->
                    response.bodyToMono<String>().flatMap { errorBody ->
                        // Try to extract errorCode and errorMessage from JSON response
                        var errorCode: String? = null
                        var errorMessage: String? = null
                        try {
                            val json = Json.parseToJsonElement(errorBody).jsonObject
                            errorCode = json["errorCode"]?.jsonPrimitive?.contentOrNull
                            errorMessage = json["errorMessage"]?.jsonPrimitive?.contentOrNull
                            LOGGER.error("Encryption failed: errorCode=$errorCode")
                        } catch (_: Exception) {
                            // Ignore parsing errors
                        }
                        reactor.core.publisher.Mono.error(
                            CryptoServiceErrorException(
                                "Crypto service returned error: $errorBody",
                                errorCode,
                                errorMessage,
                            ),
                        )
                    }
                }.awaitBody<String>()
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
                cause = e,
            )
        } catch (e: Exception) {
            throw SopsEncryptionException(
                message = "Crypto encrypt failed: ${e.message}",
                riScId = riScId,
                cause = e,
            )
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
                .onStatus({ status -> status.isError }) { response ->
                    response.bodyToMono<String>().flatMap { errorBody ->
                        // Try to extract errorCode and errorMessage from JSON response
                        var errorCode: String? = null
                        var errorMessage: String? = null
                        try {
                            val json = Json.parseToJsonElement(errorBody).jsonObject
                            errorCode = json["errorCode"]?.jsonPrimitive?.contentOrNull
                            errorMessage = json["errorMessage"]?.jsonPrimitive?.contentOrNull
                            LOGGER.error("Decryption failed: errorCode=$errorCode")
                        } catch (_: Exception) {
                            // Ignore parsing errors
                        }
                        reactor.core.publisher.Mono.error(
                            CryptoServiceErrorException(
                                "Crypto service returned error: $errorBody",
                                errorCode,
                                errorMessage,
                            ),
                        )
                    }
                }.awaitBody<RiScWithConfig>()
                .also {
                    LOGGER.info(
                        "Successfully decrypted ciphertext ${ciphertext.substring(0, min(ciphertext.length, 20))}..." +
                            "to ${it.riSc.substring(0, min(it.riSc.length, 20))}...",
                    )
                }
        } catch (e: NoSuchElementException) {
            LOGGER.error("Decryption failed: No response body received from decryption service.", e)
            throw SOPSDecryptionException(
                message = "Failed to decrypt ciphertext, no response body received from decryption service.",
                cause = e,
            )
        } catch (e: CryptoServiceErrorException) {
            throw SOPSDecryptionException(
                message = "Crypto decrypt failed: ${e.errorMessage ?: e.message}",
                errorCode = e.errorCode,
                errorMessage = e.errorMessage,
                cause = e,
            )
        } catch (e: Exception) {
            throw SOPSDecryptionException(
                message = "Crypto decrypt failed: ${e.message}",
                cause = e,
            )
        }
}
