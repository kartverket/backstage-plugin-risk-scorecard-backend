package no.risc.rosa.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class AggregatedRos(
    val RosID: String,
    val AggregatedNumber: String,
    val RemainingAggregatedNumber: String,
)

@Serializable
data class UploadRequest(
    val repository: String,
    val aggregatedros: AggregatedRos,
)

@Serializable
data class EncryptResponse(
    val sum: String,
    val remaining_sum: String,
)

@Serializable
data class EncryptRequest(
    val text: JsonElement,
)

@Serializable
data class DeleteRequest(
    val rosID: String,
)
