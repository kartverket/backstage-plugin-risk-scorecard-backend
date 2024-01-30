package no.kvros.ros

import no.kvros.github.GithubReferenceHelper
import no.kvros.github.GithubReferenceHelper.toReferenceObjects
import no.kvros.github.GithubReferenceObject
import no.kvros.infra.connector.WebClientConnector
import no.kvros.ros.models.ContentResponseDTO
import no.kvros.ros.models.ROSContentDTO
import no.kvros.ros.models.ROSDownloadUrlDTO
import no.kvros.ros.models.ROSFilenameDTO
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
    fun fetchPublishedROSes(
        owner: String,
        repository: String,
        pathToRoser: String,
        accessToken: String,
    ): List<String>? =
        fetchPublishedROSUrls(owner, repository, pathToRoser, accessToken)
            ?.map { getGithubResponse(it.download_url, accessToken) }
            ?.mapNotNull { it.rosContent()?.content }


    fun fetchPublishedROS(
        owner: String,
        repository: String,
        pathToROS: String,
        id: String,
        accessToken: String,
    ): String? =
        getGithubResponse("/$owner/$repository/contents/$pathToROS/$id.ros.yaml", accessToken).rosContent()?.content


    fun fetchDraftedROSContent(
        owner: String,
        repository: String,
        pathToROS: String,
        id: String,
        accessToken: String,
    ): String? = getGithubResponse(
        uri = GithubReferenceHelper.uriToFetchDraftedROSContent(owner, repository, id),
        accessToken = accessToken
    ).rosContent()?.content

    fun fetchPublishedROSFilenames(
        owner: String,
        repository: String,
        pathToRoser: String,
        accessToken: String,
    ): List<String>? =
        fetchPublishedROSIds(owner, repository, pathToRoser, accessToken)
            ?.map { it.name.substringBefore('.') }

    private fun fetchPublishedROSIds(
        owner: String,
        repository: String,
        pathToRoser: String,
        accessToken: String,
    ): List<ROSFilenameDTO>? =
        getGithubResponse("/$owner/$repository/contents/$pathToRoser", accessToken).rosFilenames()

    private fun fetchPublishedROSUrls(
        owner: String,
        repository: String,
        pathToRoser: String,
        accessToken: String,
    ): List<ROSDownloadUrlDTO>? =
        getGithubResponse("/$owner/$repository/contents/$pathToRoser", accessToken).downloadUrls()

    internal fun fetchLatestSHAOfFileInMain(
        owner: String,
        repository: String,
        accessToken: String,
        path: String,
    ): String? =
        getGithubResponse("/$owner/$repository/contents/$path", accessToken).contentReponseDTO()?.sha?.value


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