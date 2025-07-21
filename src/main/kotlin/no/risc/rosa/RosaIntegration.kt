package no.risc.rosa

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
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
    fun createUploadRequest(
        RiscID: String,
        aggregatedNumber: String,
        remainingAggregatedNumber: String,
        repository: String,
    ): UploadRequest {
        val aggregatedRos = AggregatedRos(RiscID, aggregatedNumber, remainingAggregatedNumber)
        val request =
            UploadRequest(
                repository,
                aggregatedRos,
            )
        return request
    }

    fun createEncryptRequest(request: String): EncryptRequest {
        val json = Json { ignoreUnknownKeys = true }
        val jsonElement: JsonElement = json.parseToJsonElement(request)
        val request = EncryptRequest(text = jsonElement)
        return request
    }

    private suspend fun encrypt(request: EncryptRequest): EncryptResponse =
        rosaConnector.webClient
            .post()
            .uri("/rosa/encrypt")
            .bodyValue(request)
            .retrieve()
            .awaitBody<EncryptResponse>()

    suspend fun sendCipher(request: UploadRequest): String =
        rosaConnector.webClient
            .post()
            .uri("/rosa")
            .bodyValue(request)
            .retrieve()
            .awaitBody()

    suspend fun deleteRiSc(riscID: String): String =
        rosaConnector.webClient
            .delete()
            .uri("/rosa/{id}", riscID)
            .retrieve()
            .awaitBody()

    suspend fun encryptAndUpload( // To-do error handling
        riScId: String,
        repository: String,
        riSc: String,
    ): String {
        val encryptRequest = createEncryptRequest(riSc)
        val encryptResponse = encrypt(encryptRequest)
        val aggregatedRos = AggregatedRos(riScId, encryptResponse.sum, encryptResponse.remaining_sum)
        val uploadRequest = UploadRequest(repository, aggregatedRos)
        val uploadResponse = sendCipher(uploadRequest)
        return uploadResponse
    }
}
