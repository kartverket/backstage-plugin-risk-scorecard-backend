package no.risc.initRiSc.model

import kotlinx.serialization.Serializable

@Serializable
data class GenerateRiScRequestBody(
    val initialRiSc: String,
    val defaultRiScId: String,
)
