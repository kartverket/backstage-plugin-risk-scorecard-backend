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
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBody

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

    /**
     * Checks if the provided GCP OAuth2 token is valid by contacting a Google APIs endpoint.
     *
     * @param token The OAuth2 token to validate.
     */
    suspend fun validateAccessToken(token: String): Boolean = fetchTokenInfo(token) != null

    /**
     * Retrieves information about the provided GCP OAuth2 token from a Google APIs endpoint. If the token is invalid,
     * `null` is returned. If the token is valid, a serialised JSON object is returned.
     *
     * @see <a href="https://cloud.google.com/docs/authentication/token-types#access">Google APIs endpoint documentation</a>
     *
     * @param token The OAuth2 token to retrieve information for.
     */
    private suspend fun fetchTokenInfo(token: String): String? =
        try {
            googleOAuthApiConnector.webClient
                .get()
                .uri("?access_token=$token")
                .retrieve()
                .awaitBody<String>()
        } catch (_: WebClientResponseException.BadRequest) {
            // The response from the endpoint has a status code of 400 if the access token is invalid.
            null
        } catch (_: Exception) {
            throw FetchException(
                "Failed to fetch GCP OAuth2 token information.",
                ProcessingStatus.FailedToFetchGCPOAuth2TokenInformation,
            )
        }

    /**
     * Fetches the IDs of projects that can be accessed by the provided GCP access token.
     *
     * @param gcpAccessToken The GCP access token to retrieve project IDs for.
     */
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
        } catch (_: Exception) {
            throw FetchException("Failed to fetch GCP projects", ProcessingStatus.FailedToFetchGcpProjectIds)
        }

    /**
     * Verifies that the given GCP access token has the provided GCP IAM permissions in the given crypto key resource.
     *
     * @param cryptoKeyResourceId The resource ID (GCP project ID) for the crypto key to test permissions for.
     * @param gcpAccessToken The GCP access token to test permission levels for.
     * @param permissions The required GCP IAM permissions.
     */
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

    /**
     * Retrieves all GCP crypto keys that can be accessed with the provided GCP access token, provided their name
     * includes either "-prod-" or is configured in the `additionalAllowedGCPKeyNames` property.
     *
     * @param gcpAccessToken The GCP access token to retrieve keys for.
     */
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
