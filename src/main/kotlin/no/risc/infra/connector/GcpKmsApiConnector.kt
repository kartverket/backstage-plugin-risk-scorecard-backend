package no.risc.infra.connector

import no.risc.exception.exceptions.FetchException
import no.risc.infra.connector.models.GCPAccessToken
import no.risc.infra.connector.models.TestIamPermissionBody
import no.risc.risc.ProcessingStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.bodyToMono

@Component
class GcpKmsApiConnector(
    baseUrl: String = "https://cloudkms.googleapis.com",
) : WebClientConnector(baseUrl) {
    companion object {
        const val USE_TO_ENCRYPT = "cloudkms.cryptoKeyVersions.useToEncrypt"
        const val USE_TO_DECRYPT = "cloudkms.cryptoKeyVersions.useToDecrypt"
    }

    fun testEncryptDecryptIamPermissions(
        cryptoKeyResourceId: String,
        gcpAccessToken: GCPAccessToken,
    ): Boolean {
        val permissionsResponse =
            webClient
                .post()
                .uri("/v1/$cryptoKeyResourceId:testIamPermissions")
                .body(BodyInserters.fromValue(TestIamPermissionBody(listOf(USE_TO_ENCRYPT, USE_TO_DECRYPT))))
                .header("Authorization", "Bearer ${gcpAccessToken.value}")
                .retrieve()
                .bodyToMono<TestIamPermissionBody>()
                .block() ?: throw FetchException(
                "Unable to test encrypt/decrypt IAM permissions for $cryptoKeyResourceId",
                ProcessingStatus.FailedToFetchGcpProjectIds,
            )
        return permissionsResponse.permissions.contains(USE_TO_ENCRYPT) && permissionsResponse.permissions.contains(USE_TO_DECRYPT)
    }
}
