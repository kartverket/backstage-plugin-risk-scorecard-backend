package no.risc.initRiSc.model

import kotlinx.serialization.Serializable
import no.risc.risc.models.DefaultRiScType

@Serializable
data class RiScTypeDescriptor(
    val riScType: DefaultRiScType,
    val listName: String,
    val listDescription: String,
    val defaultTitle: String,
    val defaultScope: String,
    val numberOfScenarios: Int?,
    val numberOfActions: Int?,
)
