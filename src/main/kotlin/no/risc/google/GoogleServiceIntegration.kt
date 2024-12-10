package no.risc.google

import no.risc.exception.exceptions.FetchException
import no.risc.google.model.FetchCryptoKeysResponse
import no.risc.google.model.FetchGcpKeyRingsResponse
import no.risc.google.model.FetchGcpProjectIdsResponse
import no.risc.google.model.GcpIamPermission
import no.risc.google.model.GcpKeyRing
import no.risc.google.model.GcpLocation
import no.risc.google.model.TestIamPermissionBody
import no.risc.infra.connector.GcpCloudResourceApiConnector
import no.risc.infra.connector.GcpKmsApiConnector
import no.risc.infra.connector.GcpKmsInventoryApiConnector
import no.risc.infra.connector.GoogleOAuthApiConnector
import no.risc.infra.connector.models.GCPAccessToken
import no.risc.risc.ProcessingStatus
import no.risc.sops.model.GcpProjectId
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.bodyToMono

@Component
class GoogleServiceIntegration(
    private val googleOAuthApiConnector: GoogleOAuthApiConnector,
    private val gcpCloudResourceApiConnector: GcpCloudResourceApiConnector,
    private val gcpKmsApiConnector: GcpKmsApiConnector,
    private val gcpKmsInventoryApiConnector: GcpKmsInventoryApiConnector,
) {
    fun validateAccessToken(token: String): Boolean = fetchTokenInfo(token) != null

    private fun fetchTokenInfo(token: String): String? =
        try {
            googleOAuthApiConnector.webClient
                .get()
                .uri("?access_token=$token")
                .retrieve()
                .bodyToMono(String::class.java)
                .block()
        } catch (e: Exception) {
            throw Exception("Invalid access token: $e")
        }

    fun fetchProjectIds(gcpAccessToken: GCPAccessToken): List<GcpProjectId>? =
        gcpCloudResourceApiConnector.webClient
            .get()
            .uri("/v1/projects")
            .header("Authorization", "Bearer ${gcpAccessToken.value}")
            .retrieve()
            .bodyToMono<FetchGcpProjectIdsResponse>()
            .block()
            ?.projects
            ?.map { GcpProjectId(it.projectId) }

    fun testIamPermissions(
        cryptoKeyResourceId: String,
        gcpAccessToken: GCPAccessToken,
        permissions: List<GcpIamPermission>,
    ): Boolean {
        val response =
            gcpKmsApiConnector.webClient
                .post()
                .uri("/v1/$cryptoKeyResourceId:testIamPermissions")
                .body(BodyInserters.fromValue(TestIamPermissionBody(permissions)))
                .header("Authorization", "Bearer ${gcpAccessToken.value}")
                .retrieve()
                .bodyToMono<TestIamPermissionBody>()
                .block() ?: throw FetchException(
                "Unable to test encrypt/decrypt IAM permissions for $cryptoKeyResourceId",
                ProcessingStatus.FailedToFetchGcpProjectIds,
            )
        return if (response.permissions != null) {
            permissions.all { it in response.permissions }
        } else {
            false
        }
    }

    fun fetchKeyRings(
        projectId: GcpProjectId,
        gcpAccessToken: GCPAccessToken,
        gcpLocation: GcpLocation = GcpLocation.EUROPE_NORTH1,
    ) = gcpKmsApiConnector.webClient
        .get()
        .uri("/v1/projects/${projectId.value}/locations/${gcpLocation.value}/keyRings")
        .header("Authorization", "Bearer ${gcpAccessToken.value}")
        .retrieve()
        .bodyToMono<FetchGcpKeyRingsResponse>()
        .block()
        ?.keyRings

    fun fetchCryptoKeys(
        gcpKeyRing: GcpKeyRing,
        gcpAccessToken: GCPAccessToken,
    ) = gcpKmsApiConnector.webClient
        .get()
        .uri("/v1/${gcpKeyRing.resourceId}/cryptoKeys")
        .header("Authorization", "Bearer ${gcpAccessToken.value}")
        .retrieve()
        .bodyToMono<FetchCryptoKeysResponse>()
        .block()
        ?.cryptoKeys

    fun fetchCryptoKeys(
        gcpProjectId: GcpProjectId,
        gcpAccessToken: GCPAccessToken,
    ) = gcpKmsInventoryApiConnector.webClient
        .get()
        .uri("/v1/projects/$gcpProjectId/cryptoKeys")
        .header("Authorization", "Bearer ${gcpAccessToken.value}")
        .retrieve()
        .bodyToMono<FetchCryptoKeysResponse>()
        .block()
        ?.cryptoKeys
}
