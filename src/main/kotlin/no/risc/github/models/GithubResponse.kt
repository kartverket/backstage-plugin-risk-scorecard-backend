@file:OptIn(ExperimentalSerializationApi::class)

package no.risc.github.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import no.risc.utils.KOffsetDateTimeSerializer
import java.time.OffsetDateTime

/**
 * A file found in a GitHub repository.
 *
 * @see <a href="https://docs.github.com/en/rest/repos/contents?apiVersion=2022-11-28#get-repository-content">Get
 *      repository content API documentation</a>
 */
@Serializable
@JsonIgnoreUnknownKeys
data class GithubFileDTO(
    val content: String,
    val sha: String,
    val name: String,
)

/**
 * Information about a specific GitHub repository.
 *
 * @see <a href="https://docs.github.com/en/rest/repos/repos?apiVersion=2022-11-28#get-a-repository">Get repository API
 *      documentation</a>
 */
@Serializable
@JsonIgnoreUnknownKeys
data class GithubRepositoryDTO(
    @SerialName("default_branch")
    val defaultBranch: String,
    val permissions: GithubRepositoryPermissions,
)

/**
 * A list of permissions a user has in a GitHub repository.
 *
 * @see GithubRepositoryDTO
 */
@Serializable
@JsonIgnoreUnknownKeys
data class GithubRepositoryPermissions(
    val admin: Boolean,
    val maintain: Boolean,
    val push: Boolean,
    val triage: Boolean,
    val pull: Boolean,
)

/**
 * An access token descriptor returned after creation by the GitHub API.
 *
 * @see <a href="https://docs.github.com/en/rest/apps/apps?apiVersion=2022-11-28#create-an-installation-access-token-for-an-app">
 *      Create an installation access token API documentation</a>
 */
@Serializable
@JsonIgnoreUnknownKeys
data class GitHubAccessTokenResponse(
    val token: String,
    @Serializable(KOffsetDateTimeSerializer::class)
    @SerialName("expires_at")
    val expiresAt: OffsetDateTime,
)

fun GitHubAccessTokenResponse.isNotExpired(): Boolean = expiresAt.isAfter(OffsetDateTime.now())

/**
 * A reference to a GitHub object. An object may either be a branch or a tag.
 *
 * @see <a href="https://docs.github.com/en/rest/git/refs?apiVersion=2022-11-28#list-matching-references">List matching
 *      references API documentation</a>
 */
@Serializable
@JsonIgnoreUnknownKeys
data class GithubReferenceObjectDTO(
    val ref: String,
    val url: String,
)

/**
 * A commit object received from the GitHub API. Used in multiple API endpoints. See the response schema of, e.g., the
 * list commits API endpoint for details.
 *
 * @see <a href="https://docs.github.com/en/rest/commits/commits?apiVersion=2022-11-28#list-commits">List commits API
 *      documentation</a>
 */
@Serializable
@JsonIgnoreUnknownKeys
data class GithubCommitObject(
    val sha: String,
    val url: String,
    val commit: GithubCommitInformation,
)

/**
 * Information about a specific git commit.
 *
 * @see GithubCommitObject
 */
@Serializable
@JsonIgnoreUnknownKeys
data class GithubCommitInformation(
    val message: String,
    val committer: GithubCommitter,
)

/**
 * Information about the user that made a git commit.
 *
 * @see GithubCommitInformation
 */
@Serializable
@JsonIgnoreUnknownKeys
data class GithubCommitter(
    @Serializable(KOffsetDateTimeSerializer::class)
    val date: OffsetDateTime,
    val name: String,
)

/**
 * A pull request object received from the GitHub API. Used in multiple API endpoints. See the response schema of, e.g.,
 * the list pull requests API endpoint for details.
 *
 * @see <a href="https://docs.github.com/en/rest/pulls/pulls?apiVersion=2022-11-28#list-pull-requests">List pull
 *      requests API documentation</a>
 */
@Serializable
@JsonIgnoreUnknownKeys
data class GithubPullRequestObject(
    @SerialName("html_url")
    val url: String,
    val title: String,
    @SerialName("created_at")
    @Serializable(KOffsetDateTimeSerializer::class)
    val createdAt: OffsetDateTime,
    val head: GithubPullRequestBranch,
    val base: GithubPullRequestBranch,
    val number: Int,
)

/**
 * A branch in a pull request object.
 */
@Serializable
@JsonIgnoreUnknownKeys
data class GithubPullRequestBranch(
    val ref: String,
)
