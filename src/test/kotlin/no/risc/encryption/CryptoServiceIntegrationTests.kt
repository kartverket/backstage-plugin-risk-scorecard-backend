package no.risc.encryption

import MockableResponse
import MockableWebClient
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import mockableResponseFromObject
import no.risc.exception.exceptions.SOPSDecryptionException
import no.risc.exception.exceptions.SopsEncryptionException
import no.risc.infra.connector.CryptoServiceConnector
import no.risc.infra.connector.models.GCPAccessToken
import no.risc.risc.models.RiScWithConfig
import no.risc.sops.model.GcpKmsEntry
import no.risc.sops.model.SopsConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.MediaType

class CryptoServiceIntegrationTests {
    private lateinit var cryptoService: CryptoServiceIntegration
    private lateinit var webClient: MockableWebClient

    @BeforeEach
    fun beforeEach() {
        webClient = MockableWebClient()
        cryptoService =
            CryptoServiceIntegration(
                cryptoServiceConnector = mockk<CryptoServiceConnector>().also { every { it.webClient } returns webClient.webClient },
            )
    }

    @Test
    fun `test encrypt`() {
        webClient.queueResponse(MockableResponse("encrypted_string", MediaType.TEXT_PLAIN))

        runBlocking {
            val response =
                cryptoService.encrypt(
                    text = "test",
                    sopsConfig =
                        SopsConfig(
                            shamir_threshold = 2,
                            key_groups = null,
                            gcp_kms = listOf(GcpKmsEntry(resource_id = "test")),
                        ),
                    gcpAccessToken = GCPAccessToken("testToken"),
                    riScId = "riScId",
                )

            assertEquals(
                "encrypted_string",
                response,
                "The returned string should be the same as the one supplied by the crypto service.",
            )
        }
    }

    @Test
    fun `test encrypt no response`() {
        runBlocking {
            assertThrows<SopsEncryptionException>("Should throw a SOPSEncryptionException on no response") {
                cryptoService.encrypt(
                    text = "test",
                    sopsConfig =
                        SopsConfig(
                            shamir_threshold = 2,
                            key_groups = null,
                            gcp_kms = listOf(GcpKmsEntry(resource_id = "test")),
                        ),
                    gcpAccessToken = GCPAccessToken("testToken"),
                    riScId = "riScId",
                )
            }
        }
    }

    @Test
    fun `test decrypt`() {
        val risc =
            RiScWithConfig(
                riSc = "test",
                sopsConfig =
                    SopsConfig(
                        shamir_threshold = 2,
                        key_groups = null,
                        gcp_kms = listOf(GcpKmsEntry(resource_id = "test")),
                    ),
            )

        webClient.queueResponse(mockableResponseFromObject(risc))

        runBlocking {
            val returnedRisc = cryptoService.decrypt("test", GCPAccessToken("testToken"))

            assertEquals(
                risc,
                returnedRisc,
                "Returned RiSc with SOPS configuration should be the same as returned from the web request.",
            )
        }

        val request = webClient.getNextRequest()
        assertTrue(
            request.headers.containsKey("gcpAccessToken"),
            "Crypto service expects the GCP Access Token to be passed as an HTTP header.",
        )

        val gcpAccessTokenHeader = request.headers.getOrDefault("gcpAccessToken", emptyList())
        assertEquals(1, gcpAccessTokenHeader.size, "There should be one GCP Access Token given in the HTTP header.")
        assertEquals(
            "testToken",
            gcpAccessTokenHeader[0],
            "The GCP Access Token in the HTTP header differs from the one passed to the decrypt function.",
        )
    }

    @Test
    fun `test decrypt unexpected answer format`() {
        webClient.queueResponse(MockableResponse("{\"test\": \"test\"}"))
        runBlocking {
            assertThrows<SOPSDecryptionException>("Should throw a SOPSDecryptionException on unexpected content") {
                cryptoService.decrypt(
                    "test",
                    GCPAccessToken("testToken"),
                )
            }
        }
    }
}
