package no.kvros.github

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import no.kvros.security.MicrosoftUser
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
    val rosId: String,
    val baseBranch: String,
) {
    fun toContentBody(): String =
        "{ \"title\":\"$title\", \"body\": \"$body\", \"head\": \"$repositoryOwner:$rosId\", \"base\": \"$baseBranch\" }"
}

data class GithubCreateNewAccessTokenForRepository(
    val repositoryName: String,
    val permissions: Map<String, String> =
        mapOf(
            "contents" to "write",
            "pull_requests" to "write",
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
    private const val rosPostfixForFiles = ".ros.yaml"
    private const val defaultPathToROSDirectory = ".security/ros"

    fun uriToFindJSONSchema(
        owner: String,
        repository: String,
        version: String,
    ): String = "/$owner/$repository/contents/schemas/ros_schema_no_v$version.json"

    fun uriToFindSopsConfig(
        owner: String,
        repository: String,
    ): String = "/$owner/$repository/contents/$defaultPathToROSDirectory/.sops.yaml"

    fun uriToFindRosFiles(
        owner: String,
        repository: String,
        rosPath: String,
    ): String = "/$owner/$repository/contents/$rosPath"

    fun uriToFindAllRosBranches(
        owner: String,
        repository: String,
    ): String = "/$owner/$repository/git/matching-refs/heads/ros-"

    fun uriToFindExistingBranchForROS(
        owner: String,
        repository: String,
        rosId: String,
    ): String = "/$owner/$repository/git/matching-refs/heads/$rosId"

    fun WebClient.ResponseSpec.toReferenceObjects(): List<GithubReferenceObject> =
        this.bodyToMono<List<GithubReferenceObjectDTO>>().block()?.map { it.toInternal() } ?: emptyList()

    fun uriToFindContentOfFileOnDraftBranch(
        owner: String,
        repository: String,
        rosId: String,
        draftBranch: String = rosId,
    ): String = "/$owner/$repository/contents/$defaultPathToROSDirectory/${rosId}$rosPostfixForFiles?ref=$draftBranch"

    fun uriToPostContentOfFileOnDraftBranch(
        owner: String,
        repository: String,
        rosId: String,
        draftBranch: String = rosId,
    ): String = "/$owner/$repository/contents/$defaultPathToROSDirectory/${rosId}$rosPostfixForFiles?ref=$draftBranch"

    fun uriToGetCommitStatus(
        owner: String,
        repository: String,
        branchName: String,
    ): String = "/$owner/$repository/commits/$branchName/status"

    fun uriToCreateNewBranchForROS(
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
        "{ \"title\":\"Closed\", \"body\": \"PR'en ble lukket da ROS ble " +
            "oppdatert. Ny godkjenning av risikoeier kreves.\",  \"state\": \"closed\"}"

    fun bodyToCreateNewPullRequest(
        repositoryOwner: String,
        rosId: String,
        rosRisikoEier: MicrosoftUser,
    ): GithubCreateNewPullRequestPayload =
        GithubCreateNewPullRequestPayload(
            title = "Branch for ros $rosId",
            body = "${rosRisikoEier.name}(${rosRisikoEier.email.value}) har godkjent ROS-analysen, noen må merge for at det skal bli registrert",
            repositoryOwner,
            rosId,
            baseBranch = "main",
        )

    fun bodyToCreateNewBranchForROSFromMain(
        rosId: String,
        latestShaAtMain: String,
    ): GithubCreateNewBranchPayload = GithubCreateNewBranchPayload("refs/heads/$rosId", latestShaAtMain)

    fun uriToFindAppInstallation(): String = "/installations"

    fun uriToGetAccessTokenFromInstallation(installationId: String): String = "/installations/$installationId/access_tokens"

    fun bodyToCreateAccessTokenForRepository(repositoryName: String): GithubCreateNewAccessTokenForRepository =
        GithubCreateNewAccessTokenForRepository(repositoryName)
}
