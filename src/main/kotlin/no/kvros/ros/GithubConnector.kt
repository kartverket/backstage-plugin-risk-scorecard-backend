package no.kvros.ros

import no.kvros.github.GithubReferenceHelper
import no.kvros.github.GithubReferenceHelper.toReferenceObjects
import no.kvros.github.GithubReferenceObject
import no.kvros.infra.connector.WebClientConnector
import no.kvros.ros.models.ContentResponseDTO
import no.kvros.ros.models.ROSContentDTO
import no.kvros.ros.models.ROSDownloadUrlDTO
import no.kvros.ros.models.ROSFilenameDTO
import org.springframework.beans.factory.annotation.Value
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
class GithubConnector(@Value("\${github.repository.ros-folder-path}") private val defaultROSPath: String) :
    WebClientConnector("https://api.github.com/repos") {
    fun fetchPublishedROS(
        owner: String,
        repository: String,
        id: String,
        accessToken: String,
    ): String? =
        getGithubResponse(
            "/$owner/$repository/contents/$defaultROSPath/$id.ros.yaml",
            accessToken
        ).rosContent()?.content


    fun fetchDraftedROSContent(
        owner: String,
        repository: String,
        id: String,
        accessToken: String,
    ): String? = getGithubResponse(
        uri = GithubReferenceHelper.uriToFetchDraftedROSContent(owner, repository, id),
        accessToken = accessToken
    ).rosContent()?.content

    fun fetchPublishedROSFilenames(
        owner: String,
        repository: String,
        accessToken: String,
    ): List<String>? =
        getGithubResponse("/$owner/$repository/contents/$defaultROSPath", accessToken).rosFilenames() // TODO : helper
            ?.map { it.name.substringBefore('.') }


    internal fun fetchLatestSHAOfFileInMain(
        owner: String,
        repository: String,
        accessToken: String,
        rosId: String,
    ): String? =
        getGithubResponse(
            "/$owner/$repository/contents/$defaultROSPath/$rosId.ros.yaml",
            accessToken
        ).contentReponseDTO()?.sha


    private fun postNewROS() {}
    private fun updateExistingROS(
        owner: String,
        repository: String,
    ) {
    }


    internal fun writeToFile(
        owner: String,
        repository: String,
        rosId: String,
        accessToken: String,
        writePayload: GithubWriteToFilePayload,
    ): String? {
        val uri = "/$owner/$repository/contents/$defaultROSPath/$rosId.ros.yaml" // TODO: helper

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


    fun fetchAllROSBranches(
        owner: String,
        repository: String,
        accessToken: String,
    ): List<GithubReferenceObject> {
        val uri = GithubReferenceHelper.uriToFindAllRosBranches(owner, repository)

        return getGithubResponse(uri, accessToken).toReferenceObjects()
    }

    private fun ResponseSpec.rosFilenames(): List<ROSFilenameDTO>? =
        this.bodyToMono<List<ROSFilenameDTO>>().block()

    private fun ResponseSpec.rosContent(): ROSContentDTO? =
        this.bodyToMono<ROSContentDTO>().block()

    private fun ResponseSpec.contentReponseDTO(): ContentResponseDTO? =
        this.bodyToMono<ContentResponseDTO>().block()


    private fun ResponseSpec.downloadUrls(): List<ROSDownloadUrlDTO>? =
        this.bodyToMono<List<ROSDownloadUrlDTO>>().block()

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