package no.risc.sops.model

import com.fasterxml.jackson.annotation.JsonProperty
import no.risc.exception.exceptions.NoResourceIdFoundException
import no.risc.risc.ProcessingStatus

data class SopsConfig(
    @JsonProperty("creation_rules") val creationRules: List<CreationRule>,
) {
    fun getGcpProjectId(): GcpProjectId {
        val resourceId =
            this.creationRules
                .firstOrNull()
                ?.keyGroups
                ?.firstOrNull { !it.gcpKms.isNullOrEmpty() }
                ?.gcpKms
                ?.firstOrNull()
                ?.resourceId
                ?: throw NoResourceIdFoundException(
                    "No gcp kms resource id could be found",
                    GetSopsConfigResponse(
                        status = ProcessingStatus.NoGcpKeyInSopsConfigFound,
                        gcpProjectId = GcpProjectId(""),
                        publicAgeKeys = emptyList(),
                        gcpProjectIds = emptyList(),
                    ),
                )
        return GcpProjectId(resourceId.split("/")[1])
    }

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
