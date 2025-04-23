package no.risc.google.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonIgnoreUnknownKeys
data class FetchGcpProjectIdsResponse(
    val projects: List<GcpProject>,
)

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonIgnoreUnknownKeys
data class GcpProject(
    val projectId: String,
)

@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonIgnoreUnknownKeys
data class TestIamPermissionBody(
    val permissions: List<GcpIamPermission>? = null,
)

@Serializable
enum class GcpIamPermission {
    @SerialName("cloudkms.cryptoKeyVersions.useToEncrypt")
    USE_TO_ENCRYPT,

    @SerialName("cloudkms.cryptoKeyVersions.useToDecrypt")
    USE_TO_DECRYPT,
}
