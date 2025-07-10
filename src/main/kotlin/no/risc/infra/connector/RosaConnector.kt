package no.risc.infra.connector
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.springframework.web.reactive.function.client.awaitBody

@Serializable
data class AggregatedRos(
    val RosID: String,
    val AggregatedNumber: String,
    val RemainingAggregatedNumber: String,
)

@Serializable
data class UploadRequest(
    val repository: String,
    val aggregatedros: AggregatedRos,
)

@Serializable
data class EncryptResponse(
    val sum: String,
    val remaining_sum: String,
)

@Serializable
data class EncryptRequest(
    val text: JsonElement,
)

@Serializable
data class DeleteRequest(
    val rosID: String,
)

class RosaConnector : WebClientConnector("http://localhost:8888") {
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

    suspend fun encrypt(request: EncryptRequest): EncryptResponse =
        webClient
            .post()
            .uri("/rosa/encrypt")
            .bodyValue(request)
            .retrieve()
            .awaitBody<EncryptResponse>()

    suspend fun sendCipher(request: UploadRequest): String =
        webClient
            .post()
            .uri("/rosa")
            .bodyValue(request)
            .retrieve()
            .awaitBody()

    suspend fun deleteRiSc(riscID: String): String =
        webClient
            .delete()
            .uri("/rosa/{id}", riscID)
            .retrieve()
            .awaitBody()
}
