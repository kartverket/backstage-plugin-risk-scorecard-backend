package no.risc.infra.connector

import no.risc.infra.connector.models.FetchGcpProjectIdsResponse
import no.risc.infra.connector.models.GCPAccessToken
import no.risc.sops.model.GcpProjectId
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.bodyToMono

@Component
class GoogleApiConnector(
    baseUrl: String = "https://cloudresourcemanager.googleapis.com",
) : WebClientConnector(baseUrl) {
    fun fetchProjectIds(gcpAccessToken: GCPAccessToken): List<GcpProjectId>? =
        webClient
            .get()
            .uri("/v1/projects")
            .header("Authorization", "Bearer ${gcpAccessToken.value}")
            .retrieve()
            .bodyToMono<FetchGcpProjectIdsResponse>()
            .block()
            ?.projects
            ?.map { GcpProjectId(it.projectId) }
}
