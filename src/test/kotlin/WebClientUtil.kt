import kotlinx.serialization.json.Json
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.net.URI

/**
 * A data class for keeping track of a response that should be returned by the mocked web client.
 */
data class MockableResponse(
    val content: String?,
    val contentType: MediaType = MediaType.APPLICATION_JSON,
    val httpStatus: HttpStatus = HttpStatus.OK,
)

/**
 * Creates a MockableResponse with content set to a JSON encoded string of the provided object.
 *
 * @param obj The object to encode as JSON.
 */
inline fun <reified T> mockableResponseFromObject(obj: T): MockableResponse = MockableResponse(content = Json.encodeToString<T>(obj))

/**
 * A data class for keeping track of the requests sent by the application through the mockable web client.
 */
data class MockableRequest(
    val content: String,
    val headers: Map<String, List<String>>,
    val url: URI,
)

/**
 * A handler for mocking a web client with tracking of received requests and queuing of custom responses. Can be used
 * by substituting a `WebClient` instance with the `webClient` property of the `MockableWebClient` instance.
 *
 * Responses can be scheduled using the `queueResponse` method, which allows either scheduling for a specific URI path
 * or a wildcard match for any request. When a request is made through the `WebClient` instance, a response is selected
 * and consumed based on the first match in the prioritised list:
 * 1. A scheduled response matching the path-part of the request url (if multiple, the oldest is used).
 * 2. A scheduled wildcard response (if multiple, the oldest is used).
 * 3. An empty 200 OK response.
 */
class MockableWebClient {
    private val wildcardResponses = mutableListOf<MockableResponse>()
    private val responses = mutableMapOf<String, List<MockableResponse>>()
    private val requests = mutableListOf<MockableRequest>()
    val webClient: WebClient =
        WebClient
            .builder()
            .exchangeFunction { handleRequest(it) }
            .build()

    /**
     * Handles a request build and sent through the web client. See the class documentation for how queued responses are
     * selected.
     *
     * @see MockableWebClient
     */
    private fun handleRequest(request: ClientRequest): Mono<ClientResponse> {
        requests.add(
            MockableRequest(
                content = request.body().toString(),
                headers = request.headers().toMap(),
                url = request.url(),
            ),
        )

        val requestPath = request.url().path

        // No matching queued up response
        if (requestPath !in responses && wildcardResponses.isEmpty()) return Mono.empty()

        val response: MockableResponse

        // Choose the closest matching response
        if (requestPath in responses) {
            response = responses[requestPath]!!.first()
            // Remove the path if there are no queued responses
            responses.compute(requestPath) { _, queuedResponses -> queuedResponses?.drop(1)?.ifEmpty { null } }
        } else {
            response = wildcardResponses.removeFirst()
        }

        var clientResponse = ClientResponse.create(response.httpStatus)
        if (response.content != null) {
            clientResponse =
                clientResponse
                    .header("Content-Type", response.contentType.toString())
                    .body(response.content)
        }

        return Mono.just(clientResponse.build())
    }

    /**
     * Queue a wildcard response
     */
    fun queueResponse(response: MockableResponse) = wildcardResponses.add(response)

    /**
     * Queue a response for a specific URI path
     */
    fun queueResponse(
        response: MockableResponse,
        path: String,
    ) = responses.compute(path) { _, existingResponses ->
        existingResponses?.plus(response) ?: listOf(response)
    }

    /**
     * Retrieves and removes the oldest request made to the web client
     */
    fun getNextRequest(): MockableRequest = requests.removeFirst()
}
