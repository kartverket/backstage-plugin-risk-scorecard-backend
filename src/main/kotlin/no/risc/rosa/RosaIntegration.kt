package no.risc.rosa

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import no.risc.exception.exceptions.RosaDeleteException
import no.risc.exception.exceptions.RosaEncryptionException
import no.risc.exception.exceptions.RosaUploadException
import no.risc.infra.connector.RosaConnector
import no.risc.rosa.models.AggregatedRos
import no.risc.rosa.models.EncryptRequest
import no.risc.rosa.models.EncryptResponse
import no.risc.rosa.models.UploadRequest
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.awaitBody

@Component
class RosaIntegration(
    private val rosaConnector: RosaConnector,
) {
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

    private suspend fun encrypt(request: EncryptRequest): EncryptResponse {
        try {
            val response =
                rosaConnector.webClient
                    .post()
                    .uri("/rosa/encrypt")
                    .bodyValue(request)
                    .retrieve()
                    .awaitBody<EncryptResponse>()
            return response
        } catch (e: Exception) {
            throw RosaEncryptionException(message = e.stackTraceToString())
        }
    }

    suspend fun sendCipher(request: UploadRequest): String {
        try {
            val response =
                rosaConnector.webClient
                    .post()
                    .uri("/rosa")
                    .bodyValue(request)
                    .retrieve()
                    .awaitBody<String>()
            return response
        } catch (e: Exception) {
            throw RosaUploadException(message = e.stackTraceToString(), request.aggregatedRos.riScId)
        }
    }

    suspend fun deleteRiSc(riScId: String): String {
        try {
            val response =
                rosaConnector.webClient
                    .delete()
                    .uri("/rosa/{id}", riScId)
                    .retrieve()
                    .awaitBody<String>()
            return response
        } catch (e: Exception) {
            throw RosaDeleteException(message = e.stackTraceToString(), riScId)
        }
    }

    suspend fun encryptAndUpload(
        riScId: String,
        repository: String,
        riSc: String,
    ): String {
        val encryptRequest = createEncryptRequest(riSc)
        val encryptResponse = encrypt(encryptRequest)
        val uploadRequest = createUploadRequest(riScId, encryptResponse.risk, encryptResponse.remainingRisk, repository)
        val uploadResponse = sendCipher(uploadRequest)
        return uploadResponse
    }
}
