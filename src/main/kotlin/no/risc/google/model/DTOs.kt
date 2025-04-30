@file:OptIn(ExperimentalSerializationApi::class)

package no.risc.google.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

@Serializable
@JsonIgnoreUnknownKeys
data class FetchGcpProjectIdsResponse(
    val projects: List<GcpProject>,
)

@Serializable
@JsonIgnoreUnknownKeys
data class GcpProject(
    val projectId: String,
)

@Serializable
@JsonIgnoreUnknownKeys
data class TestIAMPermissionBody(
    val permissions: List<GcpIAMPermission>? = null,
)

@Serializable
enum class GcpIAMPermission {
    @SerialName("cloudkms.cryptoKeyVersions.useToEncrypt")
    USE_TO_ENCRYPT,

    @SerialName("cloudkms.cryptoKeyVersions.useToDecrypt")
    USE_TO_DECRYPT,
}
