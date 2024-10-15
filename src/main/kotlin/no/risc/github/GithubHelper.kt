package no.risc.github

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import no.risc.risc.models.UserInfo
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

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
data class GithubRefShaDTO(
    val sha: String,
    val url: String,
)

data class GithubReferenceObject(
    val ref: String,
    val url: String,
    val sha: String,
)

data class GithubCreateNewBranchPayload(
    val nameOfNewBranch: String,
    val shaOfLatestMain: String,
) {
    fun toContentBody(): String = "{ \"ref\":\"$nameOfNewBranch\", \"sha\": \"$shaOfLatestMain\" }"
}

data class GithubCreateNewPullRequestPayload(
    val title: String,
    val body: String,
    val repositoryOwner: String,
    val riScId: String,
    val baseBranch: String,
) {
    fun toContentBody(): String =
        "{ \"title\":\"$title\", \"body\": \"$body\", \"head\": \"$repositoryOwner:$riScId\", \"base\": \"$baseBranch\" }"
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
    fun toContentBody(): String {
        return "{ \"repositories\": [\"$repositoryName\"], \"permissions\": { ${
            permissions.map {
                "\"${it.key}\":\"${it.value}\""
            }.joinToString(",")
        }}}"
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class GithubPullRequestObject(
    @JsonProperty("html_url")
    val url: String,
    val head: GithubPullRequestHead,
    val number: Int,
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

    fun uriToFindRiSc(
        owner: String,
        repository: String,
        id: String,
    ): String = "/$owner/$repository/contents/$riScFolderPath/$id.$filenamePostfix.yaml"

    fun uriToFindAllRiScBranches(
        owner: String,
        repository: String,
    ): String = "/$owner/$repository/git/matching-refs/heads/$filenamePrefix-"

    fun uriToFindExistingBranchForRiSc(
        owner: String,
        repository: String,
        riScId: String,
    ): String = "/$owner/$repository/git/matching-refs/heads/$riScId"

    fun uriToFindRiScOnDraftBranch(
        owner: String,
        repository: String,
        riScId: String,
        draftBranch: String = riScId,
    ): String = "/$owner/$repository/contents/$riScFolderPath/$riScId.$filenamePostfix.yaml?ref=$draftBranch"

    fun uriToPutRiScOnDraftBranch(
        owner: String,
        repository: String,
        riScId: String,
        draftBranch: String = riScId,
    ): String = "/$owner/$repository/contents/$riScFolderPath/$riScId.$filenamePostfix.yaml?ref=$draftBranch"

    fun uriToPutSopsConfigOnDraftBranch(
        owner: String,
        repository: String,
        riScId: String,
        draftBranch: String = riScId,
    ): String = "/$owner/$repository/contents/$riScFolderPath/.sops.yaml?ref=$draftBranch"

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
        riScId: String,
        requiresNewApproval: Boolean,
        riScRiskOwner: UserInfo,
    ): GithubCreateNewPullRequestPayload {
        val body =
            when (requiresNewApproval) {
                true ->
                    "${riScRiskOwner.name} (${riScRiskOwner.email}) has approved the risk scorecard. " +
                        "Merge the pull request to include the changes in the main branch."

                false -> "The risk scorecard has been updated, but does not require new approval."
            }

        return GithubCreateNewPullRequestPayload(
            title = "Updated risk scorecard",
            body = body,
            repositoryOwner,
            riScId,
            baseBranch = "main",
        )
    }

    fun bodyToCreateNewBranchForRiScFromMain(
        riScId: String,
        latestShaAtMain: String,
    ): GithubCreateNewBranchPayload = GithubCreateNewBranchPayload("refs/heads/$riScId", latestShaAtMain)

    fun uriToGetAccessTokenFromInstallation(installationId: String): String = "/installations/$installationId/access_tokens"

    fun bodyToGetAccessToken(repositoryName: String): GithubCreateNewAccessTokenForRepository =
        GithubCreateNewAccessTokenForRepository(repositoryName)

    fun uriToFetchAllCommitsOnBranchSince(
        owner: String,
        repository: String,
        branchName: String,
        since: String,
    ): String = "/$owner/$repository/commits?sha=$branchName&since=$since"

    fun uriToFetchCommitOnMain(
        owner: String,
        repository: String,
        riScId: String,
    ): String = "/$owner/$repository/commits?sha=main&path=$riScFolderPath/$riScId.$filenamePostfix.yaml"
}
