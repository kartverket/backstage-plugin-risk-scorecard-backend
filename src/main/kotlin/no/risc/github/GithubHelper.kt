package no.risc.github

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

@JsonIgnoreProperties(ignoreUnknown = true)
data class GithubReferenceObjectDTO(
    val ref: String,
    val url: String,
    @JsonProperty("object")
    val shaObject: GithubRefShaDTO,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GithubRefShaCommitCommiter(
    @JsonProperty("date") val dateTime: OffsetDateTime,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GithubRefShaCommit(
    val committer: GithubRefShaCommitCommiter,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GithubRefShaDTO(
    val sha: String,
    val url: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GithubRefCommitDTO(
    val sha: String,
    val url: String,
    val commit: GithubRefShaCommit,
)

data class GithubCreateNewBranchPayload(
    val nameOfNewBranch: String,
    val shaOfLatestDefault: String,
) {
    fun toContentBody(): String = "{ \"ref\":\"$nameOfNewBranch\", \"sha\": \"$shaOfLatestDefault\" }"
}

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

@JsonIgnoreProperties(ignoreUnknown = true)
data class GithubPullRequestObject(
    @JsonProperty("html_url")
    val url: String,
    val title: String,
    @JsonProperty("created_at")
    val createdAt: OffsetDateTime,
    val head: GithubPullRequestHead,
    val base: GithubPullRequestHead,
    val number: Int,
    val user: GitHubPullRequestUser,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubPullRequestUser(
    val login: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GithubCommitObject(
    val commit: Commit,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Commit(
    val message: String,
    val committer: Committer,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Committer(
    val date: String,
    val name: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
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
