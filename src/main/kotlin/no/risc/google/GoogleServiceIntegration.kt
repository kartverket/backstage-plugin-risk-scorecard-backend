package no.risc.google

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import no.risc.exception.exceptions.FetchException
import no.risc.google.model.FetchGcpProjectIdsResponse
import no.risc.google.model.GcpCryptoKeyObject
import no.risc.google.model.GcpIamPermission
import no.risc.google.model.GcpProjectId
import no.risc.google.model.TestIamPermissionBody
import no.risc.google.model.getRiScCryptoKey
import no.risc.google.model.getRiScCryptoKeyResourceId
import no.risc.google.model.getRiScKeyRing
import no.risc.infra.connector.GcpCloudResourceApiConnector
import no.risc.infra.connector.GcpKmsApiConnector
import no.risc.infra.connector.GoogleOAuthApiConnector
import no.risc.infra.connector.models.GCPAccessToken
import no.risc.risc.ProcessingStatus
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.awaitBody
import org.springframework.web.reactive.function.client.awaitBodyOrNull

@Component
class GoogleServiceIntegration(
    private val googleOAuthApiConnector: GoogleOAuthApiConnector,
    private val gcpCloudResourceApiConnector: GcpCloudResourceApiConnector,
    private val gcpKmsApiConnector: GcpKmsApiConnector,
    @Value("\${googleService.additionalAllowedGCPKeyNames}") private val additionalAllowedGCPKeyNames: List<String>,
) {
    companion object {
        val LOGGER: Logger = LoggerFactory.getLogger(GoogleServiceIntegration::class.java)
    }

    suspend fun validateAccessToken(token: String): Boolean = fetchTokenInfo(token) != null

    private suspend fun fetchTokenInfo(token: String): String? =
        try {
            googleOAuthApiConnector.webClient
                .get()
                .uri("?access_token=$token")
                .retrieve()
                .awaitBodyOrNull<String>()
        } catch (e: Exception) {
            throw Exception("Invalid access token: $e")
        }

    private suspend fun fetchProjectIds(gcpAccessToken: GCPAccessToken): List<GcpProjectId>? =
        gcpCloudResourceApiConnector.webClient
            .get()
            .uri("/v1/projects")
            .header("Authorization", "Bearer ${gcpAccessToken.value}")
            .retrieve()
            .awaitBodyOrNull<FetchGcpProjectIdsResponse>()
            ?.projects
            ?.map { GcpProjectId(it.projectId) }

    private suspend fun testIamPermissions(
        cryptoKeyResourceId: String,
        gcpAccessToken: GCPAccessToken,
        permissions: List<GcpIamPermission>,
    ): Boolean =
        try {
            gcpKmsApiConnector.webClient
                .post()
                .uri("/v1/$cryptoKeyResourceId:testIamPermissions")
                .body(BodyInserters.fromValue(TestIamPermissionBody(permissions)))
                .header("Authorization", "Bearer ${gcpAccessToken.value}")
                .retrieve()
                .awaitBody<TestIamPermissionBody>()
                .let { response ->
                    response.permissions != null && permissions.all { it in response.permissions }
                }
        } catch (_: Exception) {
            throw FetchException(
                "Unable to test encrypt/decrypt IAM permissions for $cryptoKeyResourceId",
                ProcessingStatus.FailedToFetchGcpProjectIds,
            )
        }

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
                                permissions = listOf(GcpIamPermission.USE_TO_ENCRYPT, GcpIamPermission.USE_TO_DECRYPT),
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
