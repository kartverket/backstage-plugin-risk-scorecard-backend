package no.risc.infra.connector

import org.springframework.http.codec.ClientCodecConfigurer
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.net.URI

abstract class WebClientConnector(
    baseURL: String,
) {
    private val allowedHost: String = requireNotNull(URI(baseURL).host) { "baseURL '$baseURL' has no host" }

    companion object {
        private fun enforceAllowedHost(allowedHost: String): ExchangeFilterFunction =
            ExchangeFilterFunction.ofRequestProcessor { request: ClientRequest ->
                val url = request.url()
                val hostAllowed = url.host.equals(allowedHost, ignoreCase = true)
                val schemeAllowed = url.scheme.equals("https", ignoreCase = true)
                if (hostAllowed && schemeAllowed) {
                    Mono.just(request)
                } else {
                    Mono.error<ClientRequest>(
                        IllegalArgumentException(
                            "Blocked outgoing request to disallowed URL '$url' (allowed host: '$allowedHost', https only)",
                        ),
                    )
                }
            }
    }

    val webClient =
        WebClient
            .builder()
            .baseUrl(baseURL)
            .filter(enforceAllowedHost(allowedHost))
            .exchangeStrategies(
                ExchangeStrategies
                    .builder()
                    .codecs { codecs: ClientCodecConfigurer ->
                        codecs.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)
                    }.build(),
            ).build()
}
