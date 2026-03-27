package no.risc.encryption

import no.risc.infra.connector.models.GCPAccessToken
import no.risc.risc.models.RiScWithConfig
import no.risc.risc.models.SopsConfig
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * No-op crypto service for local development without a running crypto service.
 * Files are stored and returned as plain (unencrypted) text.
 * Activated by the `local-crypto` or `local-sandboxed` Spring profile.
 */
@Component
@Profile("local-crypto | local-sandboxed")
class LocalCryptoService : CryptoServicePort {
    /** Encryption is a no-op: plain text is returned unchanged. */
    override suspend fun encrypt(
        text: String,
        sopsConfig: SopsConfig,
        gcpAccessToken: GCPAccessToken,
        riScId: String,
    ): String = text

    /** Decryption is a no-op: the content is treated as already plain text. */
    override suspend fun decrypt(
        ciphertext: String,
        gcpAccessToken: GCPAccessToken,
    ): RiScWithConfig =
        RiScWithConfig(
            riSc = ciphertext,
            sopsConfig = SopsConfig(shamirThreshold = 0, gcpKms = emptyList()),
        )
}
