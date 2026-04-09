package no.risc.crypto.sops.model

import kotlinx.serialization.Serializable

@Serializable
data class RiScWithConfig(
    val riSc: String,
    val sopsConfig: SopsConfig,
)
