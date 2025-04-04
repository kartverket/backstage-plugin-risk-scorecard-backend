package no.risc.risc.models

import kotlinx.serialization.Serializable
import no.risc.sops.model.SopsConfig

@Serializable
data class RiScWithConfig(
    val riSc: String,
    val sopsConfig: SopsConfig,
)
