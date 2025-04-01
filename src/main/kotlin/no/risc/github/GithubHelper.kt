package no.risc.github

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import no.risc.risc.models.UserInfo
import no.risc.sops.model.PullRequestObject
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

@JsonIgnoreProperties(ignoreUnknown = true)
data class GithubReferenceObjectDTO(
    val ref: String,
    val url: String,
    @JsonProperty("object")
    val shaObject: GithubRefShaDTO,
) {
    fun toInternal(): GithubReferenceObject = GithubReferenceObject(ref, url, shaObject.sha)
}

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
    val commit: GithubRefShaCommit,
)

data class GithubReferenceObject(
    val ref: String,
    val url: String,
    val sha: String,
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

data class GithubCreateNewAccessTokenForRepository(
    val repositoryName: String,
    val permissions: Map<String, String> =
        mapOf(
            "contents" to "write",
            "pull_requests" to "write",
            "statuses" to "read",
        ),
) {
    fun toContentBody(): String =
        "{ \"repositories\": [\"$repositoryName\"], \"permissions\": { ${
            permissions.map {
                "\"${it.key}\":\"${it.value}\""
            }.joinToString(",")
        }}}"
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
) {
    fun toPullRequestObject() =
        PullRequestObject(
            url = url,
            title = title,
            openedBy = user.login,
            createdAt = createdAt,
        )
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubPullRequestUser(
    val login: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RepositoryBranchDTO(
    val name: String,
)

enum class PullRequestFileStatus(
    val value: String,
) {
    Added("added"),
    Removed("removed"),
    Modified("modified"),
    Renamed("renamed"),
    Copied("copied"),
    Changed("changed"),
    Unchanged("unchanged"),
    ;

    companion object {
        @JsonCreator
        fun fromValue(value: String): PullRequestFileStatus? = entries.firstOrNull { it.value == value }
    }

    @JsonValue
    fun toValue(): String = value
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class PullRequestFileObject(
    val filename: String,
    val status: PullRequestFileStatus,
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
    fun uriToFindSopsConfig(
        owner: String,
        repository: String,
    ): String = "/$owner/$repository/contents/$riScFolderPath/.sops.yaml"

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

    fun uriToFindSopsConfig(
        owner: String,
        repository: String,
        id: String,
    ): String = "/$owner/$repository/contents/$riScFolderPath/.sops.yaml"

    fun uriToFindRiSc(
        owner: String,
        repository: String,
        id: String,
    ): String = "/$owner/$repository/contents/$riScFolderPath/$id.$filenamePostfix.yaml"

    fun uriToFindAllRiScBranches(
        owner: String,
        repository: String,
    ): String = "/$owner/$repository/git/matching-refs/heads/$filenamePrefix-"

    fun uriToFindAllBranches(
        owner: String,
        repository: String,
    ): String = "/$owner/$repository/branches?per_page=100"

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

    fun uriToFindSopsConfigOnDraftBranch(
        owner: String,
        repository: String,
        riScId: String,
        draftBranch: String = riScId,
    ): String = "/$owner/$repository/contents/$riScFolderPath/.sops.yaml?ref=$draftBranch"

    fun uriToPutRiScOnDraftBranch(
        owner: String,
        repository: String,
        riScId: String,
        draftBranch: String = riScId,
    ): String = "/$owner/$repository/contents/$riScFolderPath/$riScId.$filenamePostfix.yaml?ref=$draftBranch"

    fun uriToPutSopsConfigOnDraftBranch(
        owner: String,
        repository: String,
        draftBranch: String,
    ): String = "/$owner/$repository/contents/$riScFolderPath/.sops.yaml?ref=$draftBranch"

    fun uriToPutFileToGitHub(
        owner: String,
        repository: String,
        path: String,
        branch: String? = null,
    ): String = branch?.let { "/$owner/$repository/contents/$path?ref=$branch" } ?: "/$owner/$repository/contents/$path"

    fun uriToGetCommitStatus(
        owner: String,
        repository: String,
        branchName: String,
    ): String = "/$owner/$repository/commits/$branchName/status"

    fun uriToCreateNewBranchForRiSc(
        owner: String,
        repository: String,
    ): String = "/$owner/$repository/git/refs"

    fun uriToFetchAllPullRequests(
        owner: String,
        repository: String,
    ): String = "/$owner/$repository/pulls"

    fun uriToFetchPullRequestFiles(
        owner: String,
        repository: String,
        pullRequestNumber: Int,
    ): String = "/$owner/$repository/pulls/$pullRequestNumber/files"

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

    fun bodyToCreateNewPullRequest(
        repositoryOwner: String,
        requiresNewApproval: Boolean,
        riScRiskOwner: UserInfo,
        branch: String,
        baseBranch: String,
    ): GithubCreateNewPullRequestPayload {
        val body =
            when (requiresNewApproval) {
                true ->
                    "${riScRiskOwner.name} (${riScRiskOwner.email}) has approved the risk scorecard. " +
                        "Merge the pull request to include the changes in the default branch."

                false -> "The risk scorecard has been updated, but does not require new approval."
            }

        return GithubCreateNewPullRequestPayload(
            title = "Updated risk scorecard",
            body = body,
            repositoryOwner = repositoryOwner,
            branch = branch,
            baseBranch = baseBranch,
        )
    }

    fun bodyToCreateNewBranchFromDefault(
        branchName: String,
        latestShaAtDefault: String,
    ): GithubCreateNewBranchPayload =
        GithubCreateNewBranchPayload(
            nameOfNewBranch = "refs/heads/$branchName",
            shaOfLatestDefault = latestShaAtDefault,
        )

    fun uriToGetAccessTokenFromInstallation(installationId: String): String = "/installations/$installationId/access_tokens"

    fun bodyToGetAccessToken(repositoryName: String): GithubCreateNewAccessTokenForRepository =
        GithubCreateNewAccessTokenForRepository(repositoryName = repositoryName)

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
            "/$owner/$repository/commits?${ branch?.let {
                "sha=$branch"
            } ?: ""}&${ riScId?.let { "path=$riScFolderPath/$riScId.$filenamePostfix.yaml" } ?: "" }"
        }

    fun uriToFetchCommitsSince(
        owner: String,
        repository: String,
        since: OffsetDateTime,
    ): String = "/$owner/$repository/commits?since=$since"
}
