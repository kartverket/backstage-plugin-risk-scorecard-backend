package no.risc.infra.connector

import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.codec.ClientCodecConfigurer
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient

abstract class WebClientConnector(
    baseURL: String,
) {
    val webClient =
        WebClient
            .builder()
            .baseUrl(baseURL)
            .exchangeStrategies(
                ExchangeStrategies
                    .builder()
                    .codecs { codecs: ClientCodecConfigurer ->
                        codecs.defaultCodecs().maxInMemorySize(1024 * 1024)
                    }.build(),
            ).build()

    protected inline fun <reified T> typeReference() = object : ParameterizedTypeReference<T>() {}
}
