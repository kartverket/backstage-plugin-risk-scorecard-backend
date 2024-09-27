package no.risc.infra.connector

import org.springframework.web.reactive.function.client.WebClient

abstract class WebClientConnector(
    baseURL: String,
) {
    val webClient = WebClient.create(baseURL)
}
