package no.risc.risc.models

import kotlinx.serialization.Serializable

@Serializable
data class RiScWithConfig(
    val riSc: String,
    val sopsConfig: SopsConfig,
)
