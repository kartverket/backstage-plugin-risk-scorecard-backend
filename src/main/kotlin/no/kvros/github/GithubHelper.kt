package no.kvros.github

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

@JsonIgnoreProperties(ignoreUnknown = true)
data class GithubReferenceObjectDTO(
    val ref: String,
    val url: String,
    @JsonProperty("object")
    val shaObject: GithubRefShaDTO
) {
    fun toInternal(): GithubReferenceObject = GithubReferenceObject(ref, url, shaObject.sha)
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class GithubRefShaDTO(
    val sha: String,
    val url: String
)


data class GithubReferenceObject(
    val ref: String,
    val url: String,
    val sha: String
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
    val rosId: String,
    val baseBranch: String
) {
    fun toContentBody(): String =
        "{ \"title\":\"$title\", \"body\": \"$body\", \"head\": \"$repositoryOwner:$rosId\", \"base\": \"$baseBranch\" }"
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class GithubPullRequestObject(
    @JsonProperty("html_url")
    val url: String,
    val head: GithubPullRequestHead
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GithubPullRequestHead(
    val ref: String
)


object GithubHelper {
    private const val rosPostfixForFiles = ".ros.yaml"
    private const val defaultPathToROSDirectory = ".security/ros"

    fun uriToFindRosFiles(
        owner: String,
        repository: String,
        rosPath: String
    ): String = "/$owner/$repository/contents/$rosPath"

    fun uriToFindAllRosBranches(
        owner: String,
        repository: String,
    ): String = "/$owner/$repository/git/matching-refs/heads/ros-"

    fun uriToFindExistingBranchForROS(
        owner: String,
        repository: String,
        rosId: String
    ): String = "/$owner/$repository/git/matching-refs/heads/${rosId}"

    fun WebClient.ResponseSpec.toReferenceObjects(): List<GithubReferenceObject> =
        this.bodyToMono<List<GithubReferenceObjectDTO>>().block()?.map { it.toInternal() } ?: emptyList()

    fun uriToFindContentOfFileOnDraftBranch(
        owner: String,
        repository: String,
        rosId: String,
        draftBranch: String = rosId
    ): String =
        "/$owner/$repository/contents/$defaultPathToROSDirectory/${rosId}${rosPostfixForFiles}?ref=$draftBranch"

    fun uriToPostContentOfFileOnDraftBranch(
        owner: String,
        repository: String,
        rosId: String,
        draftBranch: String = rosId
    ): String =
        "/$owner/$repository/contents/$defaultPathToROSDirectory/${rosId}${rosPostfixForFiles}?ref=$draftBranch"

    fun uriToGetCommitStatus(
        owner: String,
        repository: String,
        branchName: String
    ): String = "/$owner/$repository/commits/$branchName/status"

    fun uriToCreateNewBranchForROS(
        owner: String,
        repository: String,
    ): String = "/$owner/$repository/git/refs"


    fun uriToFetchAllPullRequests(
        owner: String,
        repository: String
    ): String = "/$owner/$repository/pulls"

    fun uriToCreatePullRequest(
        owner: String,
        repository: String,
    ): String = "/$owner/$repository/pulls"

    fun bodyToCreateNewPullRequest(
        repositoryOwner: String,
        rosId: String,
    ): GithubCreateNewPullRequestPayload = GithubCreateNewPullRequestPayload(
        title = "Branch for ros $rosId",
        body = "ROS body",
        repositoryOwner,
        rosId,
        baseBranch = "main"
    )

    fun bodyToCreateNewBranchForROSFromMain(
        rosId: String,
        latestShaAtMain: String
    ): GithubCreateNewBranchPayload = GithubCreateNewBranchPayload("refs/${rosId}", latestShaAtMain)
}