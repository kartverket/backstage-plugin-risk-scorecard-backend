package no.risc.encryption.models

import kotlinx.serialization.Serializable
import no.risc.risc.models.SopsConfig

@Serializable
data class EncryptionRequest(
    val text: String,
    val config: SopsConfig,
    val gcpAccessToken: String,
    val riScId: String,
)
