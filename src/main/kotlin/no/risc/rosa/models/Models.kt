package no.risc.rosa.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class AggregatedRos(
    val riScId: String,
    val aggregatedNumber: String,
    val remainingAggregatedNumber: String,
)

@Serializable
data class UploadRequest(
    val repository: String,
    val aggregatedRos: AggregatedRos,
)

@Serializable
data class EncryptResponse(
    val sum: String,
    val remainingSum: String,
)

@Serializable
data class EncryptRequest(
    val text: JsonElement,
)

@Serializable
data class DeleteRequest(
    val riScId: String,
)
