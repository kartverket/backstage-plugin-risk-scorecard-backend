package no.kvros.ros

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import no.kvros.infra.connector.WebClientConnector
import no.kvros.ros.models.ROSContentDTO
import no.kvros.ros.models.ROSFilenameDTO
import no.kvros.ros.models.ShaResponseDTO
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono

data class GithubWriteToFilePayload(
    val message: String,
    val content: String,
    val sha: String? = null
) {
    fun toContentBody(): String =
        when (sha) {
            null -> "{\"message\":\"$message\", \"content\":\"$content\"}"
            else -> "{\"message\":\"$message\", \"content\":\"$content\", \"sha\":\"$sha\"}"
        }
}

@Component
class GithubConnector : WebClientConnector("https://api.github.com/repos") {
    fun fetchMultipleROSes(
        owner: String,
        repository: String,
        pathToRoser: String,
        accessToken: String,
    ): List<String>? =
        fetchROSUrls(owner, repository, pathToRoser, accessToken)
            ?.map { getGithubResponse(it.download_url, accessToken) }
            ?.mapNotNull { it.toROS() }


    fun fetchROS(
        owner: String,
        repository: String,
        pathToROS: String,
        id: String,
        accessToken: String,
    ): String? = fetchROSContent(owner, repository, pathToROS, id, accessToken)

    private fun fetchROSContent(
        owner: String,
        repository: String,
        pathToROS: String,
        id: String,
        accessToken: String,
    ): String? =
        getGithubResponse("/$owner/$repository/contents/$pathToROS/$id.ros.yaml", accessToken).rosContent()?.content

    fun fetchFilenamesFromROSPath(
        owner: String,
        repository: String,
        pathToRoser: String,
        accessToken: String,
    ): List<String>? =
        fetchROSIds(owner, repository, pathToRoser, accessToken)
            ?.map { it.name.substringBefore('.') }

    private fun fetchROSIds(
        owner: String,
        repository: String,
        pathToRoser: String,
        accessToken: String,
    ): List<ROSFilenameDTO>? =
        getGithubResponse("/$owner/$repository/contents/$pathToRoser", accessToken).rosFilenames()

    private fun fetchROSUrls(
        owner: String,
        repository: String,
        pathToRoser: String,
        accessToken: String,
    ): List<ROSDownloadUrlDTO>? =
        getGithubResponse("/$owner/$repository/contents/$pathToRoser", accessToken).rosDownloadUrls()

    internal fun fetchLatestSHAOfFile(
        owner: String,
        repository: String,
        accessToken: String,
        path: String,
    ): String? =
        getGithubResponse("/$owner/$repository/contents/$path", accessToken).contentReponseDTO()?.sha?.value

    internal fun fetchLatestSHAOfef(
        owner: String,
        repository: String,
        accessToken: String,
        path: String = "main",
    ): String? = getGithubResponse("/$owner/$repository/commits/$path", accessToken).shaReponseDTO()?.value

    internal fun writeToFile(
        owner: String,
        repository: String,
        path: String,
        accessToken: String,
        writePayload: GithubWriteToFilePayload,
    ): String? {
        val uri = "/$owner/$repository/contents/$path"

        return webClient
            .put()
            .uri(uri)
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "token $accessToken")
            .body(Mono.just(writePayload.toContentBody()), String::class.java)
            .retrieve()
            .bodyToMono<String>()
            .block()
    }

    private val rosPrefixForRefs = "heads/ros-"
    internal fun createNewBranch(
        // TODO flytt skriving av fil og skriving av refs til forskjellige greier
        owner: String,
        repository: String,
        path: String,
        accessToken: String,
    ): String? {
        val latestShaForMainBranch = fetchLatestSHAOfFile(owner, repository, accessToken, path)
        val uri = "/$owner/$repository/git/refs/$path"


        return null
    }

    internal fun fetchAllROSBranches(
        owner: String,
        repository: String,
        accessToken: String
    ): List<GithubRefDTO> {
        val uri = "/$owner/$repository/git/matching-refs/$rosPrefixForRefs"

        return getGithubResponse(
            uri = uri,
            accessToken = accessToken
        ).toRefObjects() ?: emptyList()
    }


    private fun ResponseSpec.rosFilenames(): List<ROSFilenameDTO>? =
        this.bodyToMono<List<ROSFilenameDTO>>().block()

    private fun ResponseSpec.rosContent(): ROSContentDTO? =
        this.bodyToMono<ROSContentDTO>().block()

    private fun ResponseSpec.contentReponseDTO(): ContentResponseDTO? =
        this.bodyToMono<ContentResponseDTO>().block()

    private fun ResponseSpec.shaReponseDTO(): ShaResponseDTO? =
        this.bodyToMono<ShaResponseDTO>().block()

    private fun ResponseSpec.toRefObjects(): List<GithubRefDTO>? =
        this.bodyToMono<List<GithubRefDTO>>().block()

    private fun getGithubResponse(
        uri: String,
        accessToken: String,
    ): ResponseSpec =
        webClient.get()
            .uri(uri)
            .header("Accept", "application/vnd.github.json")
            .header("Authorization", "token $accessToken")
            .retrieve()
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class GithubRefDTO(
    val ref: String,
    val url: String,
    @JsonProperty("object")
    val shaObject: GithubRefShaDTO
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GithubRefShaDTO(
    val sha: String,
    val url: String
)
