package no.risc.google

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import no.risc.exception.exceptions.FetchException
import no.risc.google.model.FetchGcpProjectIdsResponse
import no.risc.google.model.GcpCryptoKeyObject
import no.risc.google.model.GcpIAMPermission
import no.risc.google.model.GcpProjectId
import no.risc.google.model.TestIAMPermissionBody
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
            throw FetchException(
                "Failed to fetch GCP OAuth2 token information",
                ProcessingStatus.FailedToFetchGCPOAuth2TokenInformation,
            )
        }

    private suspend fun fetchProjectIds(gcpAccessToken: GCPAccessToken): List<GcpProjectId> =
        try {
            gcpCloudResourceApiConnector.webClient
                .get()
                .uri("/v1/projects")
                .header("Authorization", "Bearer ${gcpAccessToken.value}")
                .retrieve()
                .awaitBody<FetchGcpProjectIdsResponse>()
                .projects
                .map { GcpProjectId(it.projectId) }
        } catch (e: Exception) {
            throw FetchException("Failed to fetch GCP projects", ProcessingStatus.FailedToFetchGcpProjectIds)
        }

    private suspend fun testIAMPermissions(
        cryptoKeyResourceId: String,
        gcpAccessToken: GCPAccessToken,
        permissions: List<GcpIAMPermission>,
    ): Boolean =
        try {
            gcpKmsApiConnector.webClient
                .post()
                .uri("/v1/$cryptoKeyResourceId:testIamPermissions")
                .body(BodyInserters.fromValue(TestIAMPermissionBody(permissions)))
                .header("Authorization", "Bearer ${gcpAccessToken.value}")
                .retrieve()
                .awaitBody<TestIAMPermissionBody>()
                .let { response ->
                    response.permissions != null && permissions.all { it in response.permissions }
                }
        } catch (_: Exception) {
            throw FetchException(
                "Unable to test IAM permissions for $cryptoKeyResourceId",
                ProcessingStatus.FailedToFetchGCPIAMPermissions,
            )
        }

    suspend fun getGcpCryptoKeys(gcpAccessToken: GCPAccessToken): List<GcpCryptoKeyObject> =
        coroutineScope {
            LOGGER.info("Fetching GCP crypto keys")
            fetchProjectIds(gcpAccessToken)
                .filter { it.value.contains("-prod-") || additionalAllowedGCPKeyNames.contains(it.value) }
                .map { gcpProjectId ->
                    async(Dispatchers.IO) {
                        val hasAccess =
                            testIAMPermissions(
                                cryptoKeyResourceId = gcpProjectId.getRiScCryptoKeyResourceId(),
                                gcpAccessToken = gcpAccessToken,
                                permissions = listOf(GcpIAMPermission.USE_TO_ENCRYPT, GcpIAMPermission.USE_TO_DECRYPT),
                            )

                        GcpCryptoKeyObject(
                            projectId = gcpProjectId.value,
                            keyRing = gcpProjectId.getRiScKeyRing(),
                            name = gcpProjectId.getRiScCryptoKey(),
                            resourceId = gcpProjectId.getRiScCryptoKeyResourceId(),
                            hasEncryptDecryptAccess = hasAccess,
                        )
                    }
                }.awaitAll()
        }
}
