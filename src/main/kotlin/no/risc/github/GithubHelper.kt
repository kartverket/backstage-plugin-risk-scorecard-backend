@file:OptIn(ExperimentalSerializationApi::class)

package no.risc.github

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import no.risc.utils.KOffsetDateTimeSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

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
data class GithubRefShaDTO(
    val sha: String,
    val url: String,
)

@Serializable
@JsonIgnoreUnknownKeys
data class GithubRefCommitDTO(
    val sha: String,
    val url: String,
    val commit: GithubRefShaCommit,
)

@Serializable
data class GithubCreateNewBranchPayload(
    val nameOfNewBranch: String,
    val shaOfLatestDefault: String,
) {
    fun toContentBody(): String = "{ \"ref\":\"$nameOfNewBranch\", \"sha\": \"$shaOfLatestDefault\" }"
}

@Serializable
data class GithubCreateNewPullRequestPayload(
    val title: String,
    val body: String,
    val repositoryOwner: String,
    val branch: String,
    val baseBranch: String,
) {
    fun toContentBody(): String =
        "{ \"title\":\"$title\", \"body\": \"$body\", \"head\": \"$repositoryOwner:$branch\", \"base\": \"$baseBranch\" }"
}

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
data class GithubPullRequestHead(
    val ref: String,
)

@Component
class GithubHelper(
    @Value("\${filename.prefix}") private val filenamePrefix: String,
    @Value("\${filename.postfix}") private val filenamePostfix: String,
    @Value("\${github.repository.risc-folder-path}") private val riScFolderPath: String,
) {
    fun uriToFindRiScFiles(
        owner: String,
        repository: String,
    ): String = "/$owner/$repository/contents/$riScFolderPath"

    fun repositoryContentsUri(
        owner: String,
        repository: String,
        path: String,
        branch: String? = null,
    ): String = branch?.let { "/$owner/$repository/contents/$path?ref=$branch" } ?: "/$owner/$repository/contents/$path"

    fun uriToFindRiSc(
        owner: String,
        repository: String,
        id: String,
    ): String = "/$owner/$repository/contents/$riScFolderPath/$id.$filenamePostfix.yaml"

    fun uriToFindAllRiScBranches(
        owner: String,
        repository: String,
    ): String = "/$owner/$repository/git/matching-refs/heads/$filenamePrefix-"

    fun uriToGetRepositoryInfo(
        owner: String,
        repository: String,
    ): String = "/$owner/$repository"

    fun uriToFindRiScOnDraftBranch(
        owner: String,
        repository: String,
        riScId: String,
        draftBranch: String = riScId,
    ): String = "/$owner/$repository/contents/$riScFolderPath/$riScId.$filenamePostfix.yaml?ref=$draftBranch"

    fun uriToGetCommitStatus(
        owner: String,
        repository: String,
        branchName: String,
    ): String = "/$owner/$repository/commits/$branchName/status"

    fun uriToCreateNewBranch(
        owner: String,
        repository: String,
    ): String = "/$owner/$repository/git/refs"

    fun uriToFetchAllPullRequests(
        owner: String,
        repository: String,
    ): String = "/$owner/$repository/pulls"

    fun uriToCreatePullRequest(
        owner: String,
        repository: String,
    ): String = "/$owner/$repository/pulls"

    fun uriToEditPullRequest(
        owner: String,
        repository: String,
        pullRequestNumber: Int,
    ): String = "/$owner/$repository/pulls/$pullRequestNumber"

    fun bodyToClosePullRequest(): String =
        "{ \"title\":\"Closed\", \"body\": \"The PR was closed when risk scorecard was updated. " +
            "New approval from risk owner is required.\",  \"state\": \"closed\"}"

    fun bodyToCreateNewBranchFromDefault(
        branchName: String,
        latestShaAtDefault: String,
    ): GithubCreateNewBranchPayload =
        GithubCreateNewBranchPayload(
            nameOfNewBranch = "refs/heads/$branchName",
            shaOfLatestDefault = latestShaAtDefault,
        )

    fun uriToFetchAllCommitsOnBranchSince(
        owner: String,
        repository: String,
        branchName: String,
        since: String,
    ): String = "/$owner/$repository/commits?sha=$branchName&since=$since"

    fun uriToFetchCommit(
        owner: String,
        repository: String,
        riScId: String,
        branch: String,
    ): String = "/$owner/$repository/commits?sha=$branch&path=$riScFolderPath/$riScId.$filenamePostfix.yaml"

    fun uriToFetchCommits(
        owner: String,
        repository: String,
        riScId: String? = null,
        branch: String? = null,
    ): String =
        if (branch == null && riScId == null) {
            "/$owner/$repository/commits"
        } else {
            "/$owner/$repository/commits?${
                branch?.let {
                    "sha=$branch"
                } ?: ""
            }&${riScId?.let { "path=$riScFolderPath/$riScId.$filenamePostfix.yaml" } ?: ""}"
        }

    fun uriToFetchCommitsSince(
        owner: String,
        repository: String,
        since: OffsetDateTime,
    ): String = "/$owner/$repository/commits?since=$since"
}
