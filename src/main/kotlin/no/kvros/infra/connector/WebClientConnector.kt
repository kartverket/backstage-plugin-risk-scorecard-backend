package no.kvros.infra.connector

import org.springframework.core.ParameterizedTypeReference
import org.springframework.web.reactive.function.client.WebClient

abstract class WebClientConnector(
    baseURL: String,
) {
    val webClient = WebClient.create(baseURL)

    protected inline fun <reified T> typeReference() = object : ParameterizedTypeReference<T>() {}
}
