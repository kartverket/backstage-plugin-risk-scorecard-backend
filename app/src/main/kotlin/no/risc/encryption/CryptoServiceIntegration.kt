package no.risc.encryption

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.risc.encryption.sops.DecryptionService
import no.risc.encryption.sops.EncryptionService
import no.risc.exception.exceptions.SOPSDecryptionException
import no.risc.exception.exceptions.SopsEncryptionException
import no.risc.infra.connector.models.GCPAccessToken
import no.risc.risc.models.RiScWithConfig
import no.risc.risc.models.SopsConfig
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import kotlin.math.min

@Component
class CryptoServiceIntegration(
    private val encryptionService: EncryptionService,
    private val decryptionService: DecryptionService,
    @Value("\${sops.ageKey}") private val sopsAgeKey: String,
) {
    companion object {
        val LOGGER: Logger = LoggerFactory.getLogger(CryptoServiceIntegration::class.java)
    }

    suspend fun encrypt(
        text: String,
        sopsConfig: SopsConfig,
        gcpAccessToken: GCPAccessToken,
        riScId: String,
    ): String =
        try {
            LOGGER.debug("Trying to encrypt text: ${text.substring(0, min(text.length, 20))}...")
            withContext(Dispatchers.IO) {
                encryptionService.encrypt(
                    text = text,
                    config = sopsConfig,
                    gcpAccessToken = gcpAccessToken.value,
                    riScId = riScId,
                )
            }.also {
                LOGGER.debug("Successfully encrypted text...")
            }
        } catch (e: SopsEncryptionException) {
            throw e
        } catch (e: Exception) {
            throw SopsEncryptionException(
                message = "Crypto encrypt failed.",
                riScId = riScId,
                cause = e,
            )
        }

    suspend fun decrypt(
        ciphertext: String,
        gcpAccessToken: GCPAccessToken,
    ): RiScWithConfig =
        try {
            LOGGER.debug("Trying to decrypt ciphertext: ${ciphertext.substring(0, min(ciphertext.length, 20))}...")
            withContext(Dispatchers.IO) {
                decryptionService.decryptWithSopsConfig(
                    ciphertext = ciphertext,
                    gcpAccessToken = gcpAccessToken.value,
                    sopsAgeKey = sopsAgeKey,
                )
            }.also {
                LOGGER.debug("Successfully decrypted ciphertext...")
            }
        } catch (e: SOPSDecryptionException) {
            throw e
        } catch (e: Exception) {
            throw SOPSDecryptionException(
                message = "Crypto decrypt failed.",
                cause = e,
            )
        }
}
