package no.risc.github.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.OffsetDateTime

@JsonIgnoreProperties(ignoreUnknown = true)
data class FileContentDTO(
    val content: String,
    val sha: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class FileContentsDTO(
    val name: String,
    val path: String,
    val sha: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ShaResponseDTO(
    @JsonProperty("sha") val value: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class FileNameDTO(
    @JsonProperty("name") val value: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RepositoryDTO(
    @JsonProperty("default_branch") val defaultBranch: String,
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

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubAccessTokenResponse(
    val token: String,
    @JsonProperty("expires_at") val expiresAt: OffsetDateTime,
)

fun GitHubAccessTokenResponse.isNotExpired(): Boolean = expiresAt.isAfter(OffsetDateTime.now())
