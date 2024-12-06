package no.risc.infra.connector

import no.risc.infra.connector.models.FetchCryptoKeysResponse
import no.risc.infra.connector.models.GCPAccessToken
import no.risc.sops.model.GcpProjectId
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.bodyToMono

@Component
class GcpKmsInventoryApiConnector(
    baseUrl: String = "https://kmsinventory.googleapis.com",
) : WebClientConnector(baseUrl) {
    fun fetchCryptoKeys(
        projectId: GcpProjectId,
        gcpAccessToken: GCPAccessToken,
    ) = webClient
        .get()
        .uri("/v1/projects/${projectId.value}/cryptoKeys")
        .header("Authorization", "Bearer ${gcpAccessToken.value}")
        .retrieve()
        .bodyToMono<FetchCryptoKeysResponse>()
        .block()
        ?.cryptoKeys
}
