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
    fun toContentBody(): String = "{ \"ref\":\"refs/heads/$nameOfNewBranch\", \"sha\": \"$shaOfLatestMain\" }"
}

object GithubReferenceHelper {
    private const val rosPrefixForRefs = "heads/ros-"
    private const val rosPostfixForFiles = ".ros.yaml"
    private const val defaultPathToROSDirectory = ".security/ros"

    fun uriToFindAllRosBranches(
        owner: String,
        repository: String,
    ): String = "/$owner/$repository/git/matching-refs/$rosPrefixForRefs"

    fun uriToFindExistingBranchForROS(
        owner: String,
        repository: String,
        rosId: String
    ): String = "/$owner/$repository/git/matching-refs/${rosPrefixForRefs}${rosId}"

    fun WebClient.ResponseSpec.toReferenceObjects(): List<GithubReferenceObject> =
        this.bodyToMono<List<GithubReferenceObjectDTO>>().block()?.map { it.toInternal() } ?: emptyList()

    fun uriToFindContentOfFileOnDraftBranch(
        owner: String,
        repository: String,
        rosId: String,
        draftBranch: String = "ros-$rosId"
    ): String =
        "/$owner/$repository/contents/$defaultPathToROSDirectory/ros-${rosId}${rosPostfixForFiles}?ref=$draftBranch"

    fun uriToPostContentOfFileOnDraftBranch(
        owner: String,
        repository: String,
        rosId: String,
        draftBranch: String = "ros-$rosId"
    ): String =
        "/$owner/$repository/contents/$defaultPathToROSDirectory/ros-${rosId}${rosPostfixForFiles}?ref=$draftBranch"

    fun uriToGetCommitStatus(
        owner: String,
        repository: String,
        branchName: String
    ): String = "s/$owner/$repository/commits/$branchName/status"

    fun uriToCreateNewBranchForROS(
        owner: String,
        repository: String,
    ): String = "/$owner/$repository/git/refs"

    fun bodyToCreateNewBranchForROSFromMain(
        rosId: String,
        latestShaAtMain: String
    ): GithubCreateNewBranchPayload = GithubCreateNewBranchPayload("refs/${rosPrefixForRefs}${rosId}", latestShaAtMain)
}