package no.risc.encryption

import no.risc.infra.connector.models.GCPAccessToken
import no.risc.risc.models.RiScWithConfig
import no.risc.risc.models.SopsConfig

interface CryptoServicePort {
    suspend fun encrypt(
        text: String,
        sopsConfig: SopsConfig,
        gcpAccessToken: GCPAccessToken,
        riScId: String,
    ): String

    suspend fun decrypt(
        ciphertext: String,
        gcpAccessToken: GCPAccessToken,
    ): RiScWithConfig
}
