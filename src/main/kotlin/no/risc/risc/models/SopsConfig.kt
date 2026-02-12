package no.risc.risc.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

// {key_groups:[{gcp_kms:[{resourse, createdat, enc}], age:[{recipient, enc}]}, age: [{recipient, enc},{recipient, enc}]}, age: [{recipient, enc}]], shamir_threshold: number, lastmodified: "2025-01-31T09:41:08Z", version: string}
@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonIgnoreUnknownKeys
data class SopsConfig(
    @SerialName("shamir_threshold") val shamirThreshold: Int,
    @SerialName("key_groups") val keyGroups: List<KeyGroup>? = emptyList(),
    val kms: List<JsonElement>? = null,
    @SerialName("gcp_kms") val gcpKms: List<GcpKmsEntry>,
    val age: List<AgeEntry>? = null,
    @SerialName("lastmodified") val lastModified: String? = null,
    val mac: String? = null,
    @SerialName("unencrypted_suffix") val unencryptedSuffix: String? = null,
    @SerialName("mac_only_encrypted") val macOnlyEncrypted: Boolean? = null,
    val version: String? = null,
)

@Serializable
data class GcpKmsEntry(
    @SerialName("resource_id") val resourceId: String,
    @SerialName("created_at") val createdAt: String? = null,
    val enc: String? = null,
)

@Serializable
data class AgeEntry(
    val recipient: String,
    val enc: String? = null,
)

@Serializable
data class KeyGroup(
    @SerialName("gcp_kms") val gcpKms: List<GcpKmsEntry>? = null,
    @SerialName("hc_vault") val hcVault: List<JsonElement>? = null,
    val age: List<AgeEntry>? = null,
)
