package no.risc.sops.model

import com.fasterxml.jackson.annotation.JsonProperty

data class SopsConfig(
    @JsonProperty("creation_rules") val creationRules: List<CreationRule>,
) {
    fun getDeveloperPublicKeys(backendPublicAgeKey: String): List<PublicAgeKey> =
        this.creationRules
            .firstOrNull()
            ?.keyGroups
            ?.firstOrNull {
                it.gcpKms.isNullOrEmpty() &&
                    it.age?.all { ageKey -> ageKey != backendPublicAgeKey } == true
            }?.age
            ?.map {
                PublicAgeKey(it)
            } ?: emptyList()
}

data class CreationRule(
    @JsonProperty("path_regex") val pathRegex: String,
    @JsonProperty("shamir_threshold") val shamirThreshold: Int,
    @JsonProperty("key_groups") val keyGroups: List<KeyGroup>,
)

data class KeyGroup(
    val age: List<String>? = null,
    @JsonProperty("gcp_kms") val gcpKms: List<ResourceId>? = null,
)

data class ResourceId(
    @JsonProperty("resource_id") val resourceId: String,
)
