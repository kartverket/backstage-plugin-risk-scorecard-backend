package no.risc.initRiSc.model

import kotlinx.serialization.Serializable

@Serializable
data class RiScTypeDescriptor(
    val id: String,
    val listName: String,
    val listDescription: String,
    val defaultTitle: String,
    val defaultScope: String,
    val numberOfScenarios: Int?,
    val numberOfActions: Int?,
    val preferredEntityType: String?,
)
