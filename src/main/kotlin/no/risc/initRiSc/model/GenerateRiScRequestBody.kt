package no.risc.initRiSc.model

import kotlinx.serialization.Serializable
import no.risc.risc.models.DefaultRiScType

@Serializable
data class GenerateRiScRequestBody(
    val initialRiSc: String,
    val defaultRiScTypes: List<DefaultRiScType>,
)
