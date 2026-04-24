package no.risc.encryption

import no.risc.crypto.sops.model.RiScWithConfig
import no.risc.crypto.sops.model.SopsConfig
import no.risc.infra.connector.models.GCPAccessToken
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
    override fun encrypt(
        text: String,
        config: SopsConfig,
        gcpAccessToken: GCPAccessToken,
        riScId: String,
    ): String = text

    /** Decryption is a no-op: the content is treated as already plain text. */
    override fun decryptWithSopsConfig(
        ciphertext: String,
        gcpAccessToken: GCPAccessToken,
    ): RiScWithConfig =
        RiScWithConfig(
            riSc = ciphertext,
            sopsConfig = SopsConfig(shamir_threshold = 0, gcp_kms = emptyList()),
        )
}
