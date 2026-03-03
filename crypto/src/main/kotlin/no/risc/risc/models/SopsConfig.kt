package no.risc.risc.models

import com.fasterxml.jackson.annotation.JsonProperty
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
    @SerialName("shamir_threshold") @JsonProperty("shamir_threshold") val shamirThreshold: Int = 1,
    @SerialName("key_groups") @JsonProperty("key_groups") val keyGroups: List<KeyGroup>? = emptyList(),
    val kms: List<JsonElement>? = null,
    @SerialName("gcp_kms") @JsonProperty("gcp_kms") val gcpKms: List<GcpKmsEntry> = emptyList(),
    val age: List<AgeEntry>? = null,
    @SerialName("lastmodified") @JsonProperty("lastmodified") val lastModified: String? = null,
    val mac: String? = null,
    @SerialName("unencrypted_suffix") @JsonProperty("unencrypted_suffix") val unencryptedSuffix: String? = null,
    val version: String? = null,
)

@Serializable
data class GcpKmsEntry(
    @SerialName("resource_id") @JsonProperty("resource_id") val resourceId: String,
    @SerialName("created_at") @JsonProperty("created_at") val createdAt: String? = null,
    val enc: String? = null,
)

@Serializable
data class AgeEntry(
    val recipient: String,
    val enc: String? = null,
)

@Serializable
data class KeyGroup(
    @SerialName("gcp_kms") @JsonProperty("gcp_kms") val gcpKms: List<GcpKmsEntry>? = null,
    @SerialName("hc_vault") @JsonProperty("hc_vault") val hcVault: List<JsonElement>? = null,
    val age: List<AgeEntry>? = null,
)
