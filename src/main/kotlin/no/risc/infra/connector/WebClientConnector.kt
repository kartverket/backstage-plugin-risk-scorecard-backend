package no.risc.infra.connector

import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.http.codec.ClientCodecConfigurer
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import reactor.netty.resources.ConnectionProvider
import java.time.Duration

abstract class WebClientConnector(
    baseURL: String,
) {
    val webClient =
        WebClient
            .builder()
            .baseUrl(baseURL)
            .clientConnector(
                ReactorClientHttpConnector(
                    HttpClient
                        .create(
                            ConnectionProvider
                                .builder("custom")
                                .maxIdleTime(Duration.ofSeconds(20))
                                .evictInBackground(Duration.ofSeconds(30))
                                .build(),
                        ).responseTimeout(Duration.ofSeconds(30)),
                ),
            ).exchangeStrategies(
                ExchangeStrategies
                    .builder()
                    .codecs { codecs: ClientCodecConfigurer ->
                        codecs.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)
                    }.build(),
            ).build()
}
