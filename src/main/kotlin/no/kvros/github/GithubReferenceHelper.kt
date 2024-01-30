package no.kvros.github

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import no.kvros.ros.models.ShaResponseDTO
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono

data class GithubReferenceResult(
    val referenceObject: String,
)

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
    val shaOfLatestMain: String
) {
    fun toContentBody(): String = "{ \"ref\":\"refs/heads/$nameOfNewBranch\", \"sha\": \"$shaOfLatestMain\" }"
}

object GithubReferenceHelper {
    private const val rosPrefixForRefs = "heads/ros-"
    private const val rosPostfixForFiles = "ros.yaml"
    private const val defaultPathToROSDirectory = ".security/ros"

    fun uriToFindAllRosBranches(
        owner: String,
        repository: String,
    ): String = "/$owner/$repository/git/matching-refs/$rosPrefixForRefs"

    fun uriToFindExistingBranchForROS(
        owner: String,
        repository: String,
        rosId: String
    ): String = "/$owner/$repository/git/matching-refs/ros-$rosId"

    fun WebClient.ResponseSpec.toReferenceObjects(): List<GithubReferenceObject> =
        this.bodyToMono<List<GithubReferenceObjectDTO>>().block()?.map { it.toInternal() } ?: emptyList()

    fun uriToFetchDraftedROSContent(
        owner: String,
        repository: String,
        rosId: String
    ): String =
        "/$owner/$repository/contents/$defaultPathToROSDirectory/ros-${rosId}.${rosPostfixForFiles}?ref=ros-$rosId"

    fun uriToCreateNewBranchForROS(
        owner: String,
        repository: String,
    ): String = "/$owner/$repository/git/refs"

    fun bodyToCreateNewBranchForROSFromMain(
        rosId: String,
        latestShaAtMain: String
    ): GithubCreateNewBranchPayload = GithubCreateNewBranchPayload("refs/heads/ros-$rosId", latestShaAtMain)


    fun createNewBranch(
        owner: String,
        repository: String,
        rosId: String,
        accessToken: String,
        webClient: WebClient
    ): String? {
        val latestShaForMainBranch =
            fetchLatestSHAOfBranch(owner, repository, accessToken, webClient = webClient) ?: return null

        val uri = "/$owner/$repository/git/refs"
        val newBranchPayload = GithubCreateNewBranchPayload(
            nameOfNewBranch = rosId, shaOfLatestMain = latestShaForMainBranch
        )

        val response = writeNewRef(uri, accessToken, newBranchPayload, webClient = webClient)
        return response // TODO få på bedre returverdier og feilmeldinger
    }

    internal fun fetchLatestSHAOfBranch(
        owner: String,
        repository: String,
        accessToken: String,
        path: String = "main",
        webClient: WebClient
    ): String? = getGithubResponse("/$owner/$repository/commits/$path", accessToken, webClient).shaReponseDTO()?.value


    private fun WebClient.ResponseSpec.shaReponseDTO(): ShaResponseDTO? =
        this.bodyToMono<ShaResponseDTO>().block()

    private fun writeNewRef(
        uri: String,
        accessToken: String,
        newBranchPayload: GithubCreateNewBranchPayload,
        webClient: WebClient
    ): String? {
        return webClient
            .post()
            .uri(uri)
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "token $accessToken")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .body(Mono.just(newBranchPayload.toContentBody()), String::class.java)
            .retrieve()
            .bodyToMono<String>()
            .block()
    }

    // TODO
    private fun getGithubResponse(
        uri: String,
        accessToken: String,
        webClient: WebClient
    ): WebClient.ResponseSpec =
        webClient.get()
            .uri(uri)
            .header("Accept", "application/vnd.github.json")
            .header("Authorization", "token $accessToken")
            .retrieve()

}