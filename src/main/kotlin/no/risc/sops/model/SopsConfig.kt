package no.risc.sops.model

import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

// {key_groups:[{gcp_kms:[{resourse, createdat, enc}], age:[{recipient, enc}]}, age: [{recipient, enc},{recipient, enc}]}, age: [{recipient, enc}]], shamir_threshold: number, lastmodified: "2025-01-31T09:41:08Z", version: string}
@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonIgnoreUnknownKeys
data class SopsConfig(
    @JsonProperty("shamir_threshold") val shamir_threshold: Int,
    @JsonProperty("key_groups") val key_groups: List<KeyGroup>?,
    @JsonProperty("kms") val kms: List<JsonElement>? = null,
    @JsonProperty("gcp_kms") val gcp_kms: List<GcpKmsEntry>,
    @JsonProperty("age") val age: List<AgeEntry>? = null,
    @JsonProperty("lastmodified") val lastModified: String? = null,
    @JsonProperty("mac") val mac: String? = null,
    @JsonProperty("unencrypted_suffix") val unencryptedSuffix: String? = null,
    @JsonProperty("version") val version: String? = null,
)

@Serializable
data class GcpKmsEntry(
    @JsonProperty("resource_id") val resource_id: String,
    @JsonProperty("created_at") val created_at: String? = null,
    @JsonProperty("enc") val enc: String? = null,
)

@Serializable
data class AgeEntry(
    val recipient: String,
    val enc: String? = null,
)

@Serializable
data class KeyGroup(
    @JsonProperty("gcp_kms") val gcp_kms: List<GcpKmsEntry>? = null,
    @JsonProperty("hc_vault") val hc_vault: List<JsonElement>? = null,
    @JsonProperty("age") val age: List<AgeEntry>? = null,
)

@Serializable
data class ResourceId(
    @JsonProperty("resource_id") val resource_id: String,
)
