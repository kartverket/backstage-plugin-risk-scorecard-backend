package no.risc.github.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class FileContentDTO(
    val content: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ShaResponseDTO(
    @JsonProperty("sha")
    val value: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class FileNameDTO(
    @JsonProperty("name")
    val value: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RepositoryPermissionsDTO(
    val permissions: RepositoryPermissions,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RepositoryPermissions(
    val admin: Boolean,
    val maintain: Boolean,
    val push: Boolean,
    val triage: Boolean,
    val pull: Boolean,
)
