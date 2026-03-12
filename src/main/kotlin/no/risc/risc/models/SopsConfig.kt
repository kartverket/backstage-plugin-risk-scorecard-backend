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
    @SerialName("gcp_kms") val gcpKms: List<GcpKmsEntry>? = emptyList(),
    val age: List<AgeEntry>? = null,
    @SerialName("lastmodified") val lastModified: String? = null,
    val mac: String? = null,
    @SerialName("unencrypted_suffix") val unencryptedSuffix: String? = null,
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

/*
data class SopsConfig(
    @JsonProperty("shamir_threshold") val shamir_threshold: Int = 1,
    @JsonProperty("key_groups") val key_groups: List<KeyGroup>? = emptyList(),
    @JsonProperty("kms") val kms: List<Any>? = emptyList(),
    @JsonProperty("gcp_kms") val gcp_kms: List<GcpKmsEntry>? = emptyList(),
    @JsonProperty("azure_kv") val azureKv: List<Any>? = emptyList(),
    @JsonProperty("hc_vault") val hcVault: List<Any>? = emptyList(),
    @JsonProperty("age") val age: List<AgeEntry>? = emptyList(),
    @JsonProperty("lastmodified") val lastmodified: String? = null,
    @JsonProperty("mac") val mac: String? = null,
    @JsonProperty("pgp") val pgp: List<Any>? = null,
    @JsonProperty("unencrypted_suffix") val unencrypted_suffix: String? = null,
    @JsonProperty("version") val version: String? = null,
)

data class KeyGroup(
    @JsonProperty("gcp_kms") val gcp_kms: List<GcpKmsEntry>? = null,
    @JsonProperty("hc_vault") val hc_vault: List<Any>? = null,
    @JsonProperty("age") val age: List<AgeEntry>? = null,
)

data class GcpKmsEntry(
    @JsonProperty("resource_id") val resource_id: String,
    @JsonProperty("created_at") val created_at: String? = null,
    @JsonProperty("enc") val enc: String? = null,
)

data class AgeEntry(
    val recipient: String,
    val enc: String? = null,
)

 */
