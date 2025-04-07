import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

data class MockableResponse(
    val content: String,
    val contentType: MediaType = MediaType.APPLICATION_JSON,
    val httpStatus: HttpStatus = HttpStatus.OK,
)

inline fun <reified T> mockableResponseFromObject(obj: T): MockableResponse = MockableResponse(content = Json.encodeToString<T>(obj))

data class MockableRequest(
    val content: String,
    val headers: Map<String, List<String>>,
)

class MockableWebClient {
    private val responses = mutableListOf<MockableResponse>()
    private val requests = mutableListOf<MockableRequest>()
    val webClient: WebClient =
        WebClient
            .builder()
            .exchangeFunction { handleRequest(it) }
            .build()

    private fun handleRequest(request: ClientRequest): Mono<ClientResponse> {
        requests.add(
            MockableRequest(
                content = request.body().toString(),
                headers = request.headers().toMap(),
            ),
        )

        if (responses.isEmpty()) return Mono.empty()
        val response = responses.removeFirst()
        return Mono.just(
            ClientResponse
                .create(HttpStatus.OK)
                .header("content-type", response.contentType.toString())
                .body(response.content)
                .build(),
        )
    }

    fun queueResponse(response: MockableResponse) = responses.add(response)

    fun getNextRequest(): MockableRequest = requests.removeFirst()
}
