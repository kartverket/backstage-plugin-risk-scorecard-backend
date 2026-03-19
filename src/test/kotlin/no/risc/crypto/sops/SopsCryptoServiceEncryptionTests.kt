@file:Suppress("ktlint:standard:no-wildcard-imports")

package no.risc.crypto.sops

import no.risc.crypto.sops.model.*
import no.risc.infra.connector.models.GCPAccessToken
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SopsCryptoServiceEncryptionTests {
    private val sopsCryptoService =
        SopsCryptoService(
            SopsCryptoProperties(
                backendPublicKey = "age18m6cv8vklff5699waclec483sjwm0s3hxkqu637uapkukm65g4qqqfjlfm",
                securityTeamPublicKey = "age1t8ydajqexjzw238u6duz9h40gcaj23kfealk5r945sn8qkgvvprs6x4ele",
                securityPlatformPublicKey = "age1qrkc5q5nur6xpm4ldwg7z232nxkv2up84ugmctye70hlxw4chgjsg7ard3",
                agePrivateKey = "AGE-SECRET-KEY-TEST",
            ),
        )

    @Disabled("Integration test: requires valid GCP access token and sops binary")
    @Test
    fun `when gcp access token is available data is successfully encrypted`() {
        val gcpAccessToken = GCPAccessToken("din-access-token")
        val configWithGCPResourceAndAge =
            SopsConfig(
                shamir_threshold = 2,
                gcp_kms =
                    listOf(
                        GcpKmsEntry(
                            resource_id = @Suppress("ktlint:standard:max-line-length")
                            "projects/spire-ros-5lmr/locations/europe-west4/keyRings/rosene-team/cryptoKeys/ros-as-code-2",
                        ),
                    ),
                age = listOf(AgeEntry(recipient = "age1g9m644t5s95zk6px9mh2kctajqw3guuq2alntgfqu2au6fdz85lq4uupug")),
            )
        val encrypted =
            sopsCryptoService.encrypt(
                text = "{\"test\":\"test\"}",
                config = configWithGCPResourceAndAge,
                gcpAccessToken = gcpAccessToken,
                riScId = "ad-12314",
            )

        assertNotNull(encrypted)
        assertTrue(encrypted.contains("sops"))
    }

    @Test
    fun `when gcp access token is invalid an exception is thrown`() {
        val invalidGcpAccessToken = GCPAccessToken("feil")
        val configWithGCPResourceAndAge =
            SopsConfig(
                shamir_threshold = 2,
                gcp_kms =
                    listOf(
                        GcpKmsEntry(
                            resource_id = @Suppress("ktlint:standard:max-line-length")
                            "projects/spire-ros-5lmr/locations/europe-west4/keyRings/rosene-team/cryptoKeys/ros-as-code-2",
                        ),
                    ),
                age = listOf(AgeEntry(recipient = "age1g9m644t5s95zk6px9mh2kctajqw3guuq2alntgfqu2au6fdz85lq4uupug")),
            )

        assertThrows<Exception> {
            sopsCryptoService.encrypt(
                text = "{\"test\":\"test\"}",
                config = configWithGCPResourceAndAge,
                gcpAccessToken = invalidGcpAccessToken,
                riScId = "ad-12314",
            )
        }
    }
}
