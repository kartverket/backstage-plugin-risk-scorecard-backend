package no.risc.risc.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import no.risc.sops.model.SopsConfig

@Serializable
data class RiScWithConfig(
    val riSc: String,
    val sopsConfig: SopsConfig,
)
