package no.risc.encryption

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.risc.encryption.sops.DecryptionService
import no.risc.encryption.sops.EncryptionService
import no.risc.exception.exceptions.SOPSDecryptionException
import no.risc.exception.exceptions.SopsEncryptionException
import no.risc.infra.connector.models.GCPAccessToken
import no.risc.risc.models.GcpKmsEntry
import no.risc.risc.models.RiScWithConfig
import no.risc.risc.models.SopsConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CryptoServiceIntegrationTests {
    private lateinit var cryptoService: CryptoServiceIntegration
    private lateinit var encryptionService: EncryptionService
    private lateinit var decryptionService: DecryptionService

    private val sopsConfig =
        SopsConfig(
            shamirThreshold = 2,
            keyGroups = null,
            gcpKms = listOf(GcpKmsEntry(resourceId = "test")),
        )
    private val gcpAccessToken = GCPAccessToken("testToken")

    @BeforeEach
    fun beforeEach() {
        encryptionService = mockk()
        decryptionService = mockk()
        cryptoService =
            CryptoServiceIntegration(
                encryptionService = encryptionService,
                decryptionService = decryptionService,
                sopsAgeKey = "AGE-SECRET-KEY-1JXMRGWL3HYZXZPYF98YHEF0AHZR8J8J58YWAYUS8448Q9QE0AW2S3KK5GT",
            )
    }

    @Test
    fun `test encrypt`() {
        every { encryptionService.encrypt(any(), any(), any(), any()) } returns "encrypted_string"

        runBlocking {
            val response =
                cryptoService.encrypt(
                    text = "test",
                    sopsConfig = sopsConfig,
                    gcpAccessToken = gcpAccessToken,
                    riScId = "riScId",
                )

            assertEquals(
                "encrypted_string",
                response,
                "The returned string should be the same as the one returned by EncryptionService.",
            )
        }
    }

    @Test
    fun `test encrypt throws SopsEncryptionException on failure`() {
        every {
            encryptionService.encrypt(any(), any(), any(), any())
        } throws RuntimeException("SOPS failed")

        runBlocking {
            assertThrows<SopsEncryptionException>("Should throw a SopsEncryptionException on failure") {
                cryptoService.encrypt(
                    text = "test",
                    sopsConfig = sopsConfig,
                    gcpAccessToken = gcpAccessToken,
                    riScId = "riScId",
                )
            }
        }
    }

    @Test
    fun `test decrypt`() {
        val risc = RiScWithConfig(riSc = "test", sopsConfig = sopsConfig)
        every { decryptionService.decryptWithSopsConfig(any(), any(), any()) } returns risc

        runBlocking {
            val returnedRisc = cryptoService.decrypt("test", gcpAccessToken)

            assertEquals(
                risc,
                returnedRisc,
                "Returned RiSc with SOPS configuration should be the same as returned from DecryptionService.",
            )
        }
    }

    @Test
    fun `test decrypt throws SOPSDecryptionException on failure`() {
        every {
            decryptionService.decryptWithSopsConfig(any(), any(), any())
        } throws RuntimeException("SOPS failed")

        runBlocking {
            assertThrows<SOPSDecryptionException>("Should throw a SOPSDecryptionException on failure") {
                cryptoService.decrypt("test", gcpAccessToken)
            }
        }
    }
}
