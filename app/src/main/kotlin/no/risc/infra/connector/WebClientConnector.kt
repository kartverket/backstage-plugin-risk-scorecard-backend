package no.risc.infra.connector

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
                        codecs.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)
                    }.build(),
            ).build()
}
