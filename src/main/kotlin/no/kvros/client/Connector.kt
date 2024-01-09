package no.kvros.client

import org.springframework.core.ParameterizedTypeReference
import org.springframework.web.reactive.function.client.WebClient

abstract class Connector(
    private val webClientBaseUrl: String,
) {
    protected val webClient = WebClient.create(webClientBaseUrl)

    protected inline fun <reified T> typeReference() = object : ParameterizedTypeReference<T>() {}
}
