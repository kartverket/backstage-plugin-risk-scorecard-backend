package no.risc.github

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import no.risc.security.MicrosoftUser
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

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
data class GithubPullRequestHead(
    val ref: String,
)

object GithubHelper {

    fun uriToFindSopsConfig(
        owner: String,
        repository: String,
        riScFolderPath: String,
    ): String = "/$owner/$repository/contents/$riScFolderPath/.sops.yaml"

    fun uriToFindRiScFiles(
        owner: String,
        repository: String,
        riScFolderPath: String,
    ): String = "/$owner/$repository/contents/$riScFolderPath"

    fun uriToFindAllRiScBranches(
        owner: String,
        repository: String,
        riScFilePrefix: String,
    ): String = "/$owner/$repository/git/matching-refs/heads/$riScFilePrefix-"

    fun uriToFindExistingBranchForRiSc(
        owner: String,
        repository: String,
        riScId: String,
    ): String = "/$owner/$repository/git/matching-refs/heads/$riScId"

    fun WebClient.ResponseSpec.toReferenceObjects(): List<GithubReferenceObject> =
        this.bodyToMono<List<GithubReferenceObjectDTO>>().block()?.map { it.toInternal() } ?: emptyList()

    fun uriToFindContentOfFileOnDraftBranch(
        owner: String,
        repository: String,
        riScId: String,
        draftBranch: String = riScId,
        filenamePostfix: String,
        riScFolderPath: String,
    ): String = "/$owner/$repository/contents/$riScFolderPath/$riScId.$filenamePostfix.yaml?ref=$draftBranch"

    fun uriToPostContentOfFileOnDraftBranch(
        owner: String,
        repository: String,
        riScId: String,
        draftBranch: String = riScId,
        filenamePostfix: String,
        riScFolderPath: String,
    ): String = "/$owner/$repository/contents/$riScFolderPath/$riScId.$filenamePostfix.yaml?ref=$draftBranch"

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
        "{ \"title\":\"Closed\", \"body\": \"The PR was closed when RiSc was updated updated. New approval from risk owner is required.\",  \"state\": \"closed\"}"

    fun bodyToCreateNewPullRequest(
        repositoryOwner: String,
        riScId: String,
        requiresNewApproval: Boolean,
        riScRiskOwner: MicrosoftUser,
    ): GithubCreateNewPullRequestPayload {
        val body =
            if (requiresNewApproval) {
                "${riScRiskOwner.name}(${riScRiskOwner.email.value}) has approved the RiSc. Merge the PR to include the changes in the main branch."
            } else {
                "The RiSc has been updated, but does not require new approval."
            }

        return GithubCreateNewPullRequestPayload(
            title = "Branch for RiSc $riScId",
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

    fun uriToFindAppInstallation(): String = "/installations"

    fun uriToGetAccessTokenFromInstallation(installationId: String): String = "/installations/$installationId/access_tokens"

    fun bodyToCreateAccessTokenForRepository(repositoryName: String): GithubCreateNewAccessTokenForRepository =
        GithubCreateNewAccessTokenForRepository(repositoryName)
}
