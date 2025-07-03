package no.risc.infra.connector
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.springframework.web.reactive.function.client.awaitBody

@Serializable
data class ComponentRequest(
    val Backstage_UID: String,
    val GitHubNode_UID: String,
    val AggregatedNumber: String,
    val RemainingAggregatedNumber: String,
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

class RosaConnector : WebClientConnector("http://localhost:8888") {
    fun createComponent(
        backstage_uid: String,
        github_node_uid: String,
        aggregated_number: String,
        remaining_aggregated_number: String,
    ): ComponentRequest {
        val request =
            ComponentRequest(
                backstage_uid,
                github_node_uid,
                aggregated_number,
                remaining_aggregated_number,
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

    suspend fun sendCipher(request: ComponentRequest): String =
        webClient
            .post()
            .uri("/rosa")
            .bodyValue(request)
            .retrieve()
            .awaitBody()
}
