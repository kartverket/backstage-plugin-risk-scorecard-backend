package no.risc.rosa.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class AggregatedRos(
    val riScId: String,
    val aggregatedRisk: String,
    val remainingAggregatedRisk: String,
)

@Serializable
data class UploadRequest(
    val repository: String,
    val aggregatedRos: AggregatedRos,
)

@Serializable
data class EncryptResponse(
    val risk: String,
    val remainingRisk: String,
)

@Serializable
data class EncryptRequest(
    val text: JsonElement,
)

@Serializable
data class DeleteRequest(
    val riScId: String,
)
