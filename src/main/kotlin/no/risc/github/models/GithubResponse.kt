@file:OptIn(ExperimentalSerializationApi::class)

package no.risc.github.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import no.risc.utils.KOffsetDateTimeSerializer
import java.time.OffsetDateTime

@Serializable
@JsonIgnoreUnknownKeys
data class FileContentDTO(
    val content: String,
    val sha: String,
)

@Serializable
@JsonIgnoreUnknownKeys
data class ShaResponseDTO(
    @SerialName("sha")
    val value: String,
)

@Serializable
@JsonIgnoreUnknownKeys
data class FileNameDTO(
    @SerialName("name")
    val value: String,
)

@Serializable
@JsonIgnoreUnknownKeys
data class RepositoryDTO(
    @SerialName("default_branch")
    val defaultBranch: String,
    val permissions: RepositoryPermissions,
)

@Serializable
@JsonIgnoreUnknownKeys
data class RepositoryPermissions(
    val admin: Boolean,
    val maintain: Boolean,
    val push: Boolean,
    val triage: Boolean,
    val pull: Boolean,
)

@Serializable
@JsonIgnoreUnknownKeys
data class GitHubAccessTokenResponse(
    val token: String,
    @Serializable(KOffsetDateTimeSerializer::class)
    @SerialName("expires_at")
    val expiresAt: OffsetDateTime,
)

fun GitHubAccessTokenResponse.isNotExpired(): Boolean = expiresAt.isAfter(OffsetDateTime.now())
