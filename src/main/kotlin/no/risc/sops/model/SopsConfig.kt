package no.risc.sops.model

import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
// {key_groups:[{gcp_kms:[{resourse, createdat, enc}], age:[{recipient, enc}]}, age: [{recipient, enc},{recipient, enc}]}, age: [{recipient, enc}]], shamir_threshold: number, lastmodified: "2025-01-31T09:41:08Z", version: string}
@Serializable
data class SopsConfig(
    @JsonProperty("shamir_threshold") val shamir_threshold: Int,
    @JsonProperty("key_groups") val key_groups: List<KeyGroup>,
    @JsonProperty("kms") val kms: List<JsonElement>? = null,
    @JsonProperty("gcp_kms") val gcpKms: List<GcpKmsEntry>? = null,
    @JsonProperty("age") val age: List<AgeEntry>? = null,
    @JsonProperty("lastmodified") val lastModified: String? = null,
    @JsonProperty("mac") val mac: String? = null,
    @JsonProperty("unencrypted_suffix") val unencryptedSuffix: String? = null,
    @JsonProperty("version") val version: String? = null,
) {
    fun getDeveloperPublicKeys(backendPublicAgeKey: String): List<PublicAgeKey> =
        this.key_groups
            .firstOrNull {
                it.gcp_kms.isNullOrEmpty() &&
                    it.age?.all { ageKey -> ageKey.recipient != backendPublicAgeKey } == true
            }?.age
            ?.map {
                PublicAgeKey(it.recipient)
            } ?: emptyList()
}

@Serializable
data class GcpKmsEntry(
    @JsonProperty("resource_id") val resourceId: String,
    @JsonProperty("created_at") val createdAt: String? = null,
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
    @JsonProperty("resource_id") val resourceId: String,
)
