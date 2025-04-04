package no.risc.google

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import no.risc.exception.exceptions.FetchException
import no.risc.google.model.FetchCryptoKeysResponse
import no.risc.google.model.FetchGcpKeyRingsResponse
import no.risc.google.model.FetchGcpProjectIdsResponse
import no.risc.google.model.GcpIamPermission
import no.risc.google.model.GcpKeyRing
import no.risc.google.model.GcpLocation
import no.risc.google.model.GcpProjectId
import no.risc.google.model.TestIamPermissionBody
import no.risc.google.model.getRiScCryptoKey
import no.risc.google.model.getRiScCryptoKeyResourceId
import no.risc.google.model.getRiScKeyRing
import no.risc.infra.connector.GcpCloudResourceApiConnector
import no.risc.infra.connector.GcpKmsApiConnector
import no.risc.infra.connector.GcpKmsInventoryApiConnector
import no.risc.infra.connector.GoogleOAuthApiConnector
import no.risc.infra.connector.models.GCPAccessToken
import no.risc.risc.ProcessingStatus
import no.risc.sops.model.GcpCryptoKeyObject
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.bodyToMono

@Component
class GoogleServiceIntegration(
    private val googleOAuthApiConnector: GoogleOAuthApiConnector,
    private val gcpCloudResourceApiConnector: GcpCloudResourceApiConnector,
    private val gcpKmsApiConnector: GcpKmsApiConnector,
    private val gcpKmsInventoryApiConnector: GcpKmsInventoryApiConnector,
    @Value("\${googleService.additionalAllowedGCPKeyNames}") private val additionalAllowedGCPKeyNames: List<String>,
) {
    companion object {
        val LOGGER = LoggerFactory.getLogger(GoogleServiceIntegration::class.java)
    }

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

    suspend fun getGcpCryptoKeys(gcpAccessToken: GCPAccessToken): List<GcpCryptoKeyObject> =
        coroutineScope {
            LOGGER.info("Fetching GCP crypto keys")
            val gcpProjectIds =
                fetchProjectIds(gcpAccessToken)
                    ?: throw FetchException(
                        "Failed to fetch GCP projects",
                        ProcessingStatus.FailedToFetchGcpProjectIds,
                    )
            gcpProjectIds
                .filter { it.value.contains("-prod-") || additionalAllowedGCPKeyNames.contains(it.value) }
                .map {
                    it to
                        async(Dispatchers.IO) {
                            testIamPermissions(
                                cryptoKeyResourceId = it.getRiScCryptoKeyResourceId(),
                                gcpAccessToken = gcpAccessToken,
                                permissions = GcpIamPermission.ENCRYPT_DECRYPT,
                            )
                        }
                }.map { (gcpProjectId, hasAccess) ->
                    GcpCryptoKeyObject(
                        projectId = gcpProjectId.value,
                        keyRing = gcpProjectId.getRiScKeyRing(),
                        name = gcpProjectId.getRiScCryptoKey(),
                        resourceId = gcpProjectId.getRiScCryptoKeyResourceId(),
                        hasEncryptDecryptAccess = hasAccess.await(),
                    )
                }.toMutableList()
        }
}
