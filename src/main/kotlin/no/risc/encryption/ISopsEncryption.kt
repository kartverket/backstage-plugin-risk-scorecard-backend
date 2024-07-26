package no.risc.encryption

import no.risc.infra.connector.models.GCPAccessToken

interface ISopsEncryption {
    fun encrypt(
        text: String,
        config: String,
        gcpAccessToken: GCPAccessToken,
        riScId: String
    ): String

    fun decrypt(
        ciphertext: String,
        gcpAccessToken: GCPAccessToken,
        agePrivateKey: String,
    ): String

}