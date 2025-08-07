package no.risc.rosa

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import no.risc.infra.connector.RosaConnector
import no.risc.rosa.models.AggregatedRos
import no.risc.rosa.models.EncryptRequest
import no.risc.rosa.models.EncryptResponse
import no.risc.rosa.models.UploadRequest
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.awaitBody

@Component
class RosaIntegration(
    private val rosaConnector: RosaConnector,
) {
    companion object {
        val LOGGER: Logger = LoggerFactory.getLogger(RosaIntegration::class.java)
    }

    private suspend fun createUploadRequest(
        riScId: String,
        aggregatedRisk: String,
        remainingAggregatedRisk: String,
        repository: String,
    ): UploadRequest {
        val aggregatedRos = AggregatedRos(riScId, aggregatedRisk, remainingAggregatedRisk)
        val request =
            UploadRequest(
                repository,
                aggregatedRos,
            )
        return request
    }

    private suspend fun createEncryptRequest(request: String): EncryptRequest {
        val json = Json { ignoreUnknownKeys = true }
        val jsonElement: JsonElement = json.parseToJsonElement(request)
        val request = EncryptRequest(text = jsonElement)
        return request
    }

    private suspend fun encrypt(request: EncryptRequest): EncryptResponse? =
        try {
            LOGGER.info("Sending POST-request to /rosa/encrypt")
            rosaConnector.webClient
                .post()
                .uri("/rosa/encrypt")
                .bodyValue(request)
                .retrieve()
                .awaitBody<EncryptResponse>()
        } catch (e: Exception) {
            LOGGER.warn("Failed to encrypt ROS", e)
            null
        }

    suspend fun sendCipher(request: UploadRequest): String? =
        try {
            LOGGER.info("Sending POST-request to /rosa")
            rosaConnector.webClient
                .post()
                .uri("/rosa")
                .bodyValue(request)
                .retrieve()
                .awaitBody<String>()
        } catch (e: Exception) {
            LOGGER.warn("Failed to upload ROS to Rosa", e)
            null
        }

    suspend fun deleteRiSc(riScId: String): String? =
        try {
            LOGGER.info("Sending DELETE-request to /rosa")
            rosaConnector.webClient
                .delete()
                .uri("/rosa/{id}", riScId)
                .retrieve()
                .awaitBody<String>()
        } catch (e: Exception) {
            LOGGER.warn("DELETE-request to Rosa failed: ", e)
            null
        }

    suspend fun encryptAndUpload(
        riScId: String,
        repository: String,
        riSc: String,
    ): String? {
        val encryptRequest = createEncryptRequest(riSc)
        val encryptResponse = encrypt(encryptRequest) ?: return null // fail silently

        val uploadRequest =
            createUploadRequest(
                riScId,
                encryptResponse.risk,
                encryptResponse.remainingRisk,
                repository,
            )

        return sendCipher(uploadRequest)
    }
}
