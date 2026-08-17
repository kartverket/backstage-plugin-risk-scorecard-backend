package no.risc.encryption

import no.risc.crypto.sops.model.RiScWithConfig
import no.risc.crypto.sops.model.SopsConfig
import no.risc.infra.connector.models.GCPAccessToken

interface CryptoServicePort {
    fun encrypt(
        text: String,
        config: SopsConfig,
        gcpAccessToken: GCPAccessToken,
        riScId: String,
    ): String

    fun decryptWithSopsConfig(
        ciphertext: String,
        gcpAccessToken: GCPAccessToken,
    ): RiScWithConfig
}
