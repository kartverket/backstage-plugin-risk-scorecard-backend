import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.reactivestreams.Publisher
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferFactory
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.http.HttpCookie
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.reactive.ClientHttpRequest
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.net.URI
import java.util.function.Supplier

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
    val path: String,
    val method: HttpMethod,
)

/**
 * Attempts to deserialize the content (body) of the request to the specified type. This assumes that the content is
 * serialized JSON.
 */
inline fun <reified T> MockableRequest.deserializeContent(): T = Json.decodeFromString<T>(content)

/**
 * A custom ClientHttpRequest implementation, that consumes and tracks the full body of the request. All methods that
 * are non-essential for this functionality are left unimplemented.
 */
private class BodyCapturingClientHttpRequest : ClientHttpRequest {
    var body = ""

    override fun bufferFactory(): DataBufferFactory = DefaultDataBufferFactory.sharedInstance

    override fun beforeCommit(action: Supplier<out Mono<Void?>?>) = throw NotImplementedError()

    override fun isCommitted(): Boolean = throw NotImplementedError()

    override fun writeWith(body: Publisher<out DataBuffer?>): Mono<Void?> {
        this.body +=
            Mono
                .from(body)
                .block()
                ?.asInputStream()
                ?.readAllBytes()
                ?.toString(Charsets.UTF_8)
                ?: ""

        return Mono.empty()
    }

    override fun writeAndFlushWith(body: Publisher<out Publisher<out DataBuffer?>?>): Mono<Void?> = throw NotImplementedError()

    override fun setComplete(): Mono<Void?> = Mono.empty()

    override fun getMethod(): HttpMethod = throw NotImplementedError()

    override fun getURI(): URI = throw NotImplementedError()

    override fun getCookies(): MultiValueMap<String?, HttpCookie?> = LinkedMultiValueMap()

    override fun getAttributes(): Map<String?, Any?> = mutableMapOf()

    override fun <T : Any?> getNativeRequest(): T & Any = throw NotImplementedError()

    override fun getHeaders(): HttpHeaders = HttpHeaders()
}

/**
 * A handler for mocking a web client with tracking of received requests and queuing of custom responses. Can be used
 * by substituting a `WebClient` instance with the `webClient` property of the `MockableWebClient` instance.
 *
 * Responses can be scheduled using the `queueResponse` method, which allows scheduling for a specific URI path (with or
 * without a specific HTTP method) or a wildcard match for any request. When a request is made through the `WebClient`
 * instance, a response is selected and consumed based on the first match in the prioritised list:
 * 1. A scheduled response matching the path-part of the request url and the specified HTTP method (if multiple, the
 *    oldest is used)
 * 2. A scheduled response matching the path-part of the request url (if multiple, the oldest is used).
 * 3. A scheduled wildcard response (if multiple, the oldest is used).
 * 4. An empty 200 OK response.
 */
class MockableWebClient {
    private val responses = mutableMapOf<Pair<HttpMethod?, String?>, List<MockableResponse>>()
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
        // Capture request body, if any
        val bodyCapturingClient = BodyCapturingClientHttpRequest()
        runBlocking { request.writeTo(bodyCapturingClient, ExchangeStrategies.withDefaults()).block() }

        val queryParameters = request.url().query
        val requestPath =
            if (queryParameters.isNullOrBlank()) request.url().path else "${request.url().path}?$queryParameters"
        val requestMethod = request.method()

        requests
            .add(
                MockableRequest(
                    content = bodyCapturingClient.body,
                    headers = request.headers().toMap(),
                    path = requestPath,
                    method = requestMethod,
                ),
            )

        val matchedMethod: HttpMethod?
        val matchedPath: String?

        // Choose the closest matching response
        if (requestMethod to requestPath in responses) {
            // 1. Match on both HTTP method and path
            matchedMethod = requestMethod
            matchedPath = requestPath
        } else if (null to requestPath in responses) {
            // 2. Match on only path
            matchedMethod = null
            matchedPath = requestPath
        } else if (null to null in responses) {
            // 3. Only a wildcard match
            matchedMethod = null
            matchedPath = null
        } else {
            // 4. No matching response queued up
            return Mono.empty()
        }

        val response: MockableResponse = responses[matchedMethod to matchedPath]!!.first()
        // Remove the path if there are no queued responses
        responses.compute(matchedMethod to matchedPath) { _, queuedResponses -> queuedResponses?.drop(1)?.ifEmpty { null } }

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
     * Queue up a response.
     *
     * @param response The response to provide
     * @param path The path to match on. If `null`, then the response matches with any path.
     * @param method The HTTP method to match on. If `null`, then the response matches with any HTTP method.
     */
    fun queueResponse(
        response: MockableResponse,
        path: String? = null,
        method: HttpMethod? = null,
    ) = responses.compute(method to path) { _, existingResponses ->
        existingResponses?.plus(response) ?: listOf(response)
    }

    /**
     * Retrieves and removes the oldest request made to the web client
     */
    fun getNextRequest(): MockableRequest = requests.removeFirst()

    /**
     * Retrieves and removes the oldest request made to the web client at the specified path.
     *
     * @param path The path to match on.
     * @throws NoSuchElementException If no requests have been made to the given path
     */
    fun getNextRequest(path: String): MockableRequest = requests.first { it.path == path }.also { requests.remove(it) }

    /**
     * Retrieves and removes the oldest request made to the web client at the specified path with the specified HTTP method.
     *
     * @param path The path to match on.
     * @param method The HTTP method to match on.
     * @throws NoSuchElementException If no requests have been made to the given path
     */
    fun getNextRequest(
        path: String,
        method: HttpMethod,
    ): MockableRequest = requests.first { it.path == path && it.method == method }.also { requests.remove(it) }

    /**
     * Indicates whether there are responses queued up for a given path that have not yet been consumed. If there are
     * wildcard responses queued up, then the method will always return true.
     *
     * @param path The path to match on.
     */
    fun hasQueuedUpResponses(path: String?): Boolean =
        responses.any { (match, responseList) -> (match.second == path || match.second == null) && responseList.isNotEmpty() }
}
