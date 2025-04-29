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
    val sha: String,
)

@Serializable
@JsonIgnoreUnknownKeys
data class FileNameDTO(
    val name: String,
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

@Serializable
@JsonIgnoreUnknownKeys
data class GithubReferenceObjectDTO(
    val ref: String,
    val url: String,
    @SerialName("object")
    val shaObject: GithubRefShaDTO,
)

@Serializable
@JsonIgnoreUnknownKeys
data class GithubRefShaDTO(
    val sha: String,
    val url: String,
)

@Serializable
@JsonIgnoreUnknownKeys
data class GithubRefShaCommitCommiter(
    @Serializable(KOffsetDateTimeSerializer::class)
    @SerialName("date")
    val dateTime: OffsetDateTime,
)

@Serializable
@JsonIgnoreUnknownKeys
data class GithubRefShaCommit(
    val committer: GithubRefShaCommitCommiter,
)

@Serializable
@JsonIgnoreUnknownKeys
data class GithubRefCommitDTO(
    val sha: String,
    val url: String,
    val commit: GithubRefShaCommit,
)

@Serializable
@JsonIgnoreUnknownKeys
data class GithubCommitObject(
    val commit: Commit,
)

@Serializable
@JsonIgnoreUnknownKeys
data class Commit(
    val message: String,
    val committer: Committer,
)

@Serializable
@JsonIgnoreUnknownKeys
data class Committer(
    val date: String,
    val name: String,
)

@Serializable
@JsonIgnoreUnknownKeys
data class GithubPullRequestObject(
    @SerialName("html_url")
    val url: String,
    val title: String,
    @SerialName("created_at")
    @Serializable(KOffsetDateTimeSerializer::class)
    val createdAt: OffsetDateTime,
    val head: GithubPullRequestHead,
    val base: GithubPullRequestHead,
    val number: Int,
    val user: GitHubPullRequestUser,
)

@Serializable
@JsonIgnoreUnknownKeys
data class GitHubPullRequestUser(
    val login: String,
)

@Serializable
@JsonIgnoreUnknownKeys
data class GithubPullRequestHead(
    val ref: String,
)
