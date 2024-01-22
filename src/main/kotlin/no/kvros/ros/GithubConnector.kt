package no.kvros.ros

import no.kvros.infra.connector.WebClientConnector
import no.kvros.ros.models.ROSDownloadUrlDTO
import no.kvros.ros.models.ShaResponseDTO
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono

data class GithubWritePayload(
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
    fun fetchROSesFromGithub(
        owner: String,
        repository: String,
        pathToRoser: String,
        accessToken: String,
    ): List<String>? =
        fetchROSUrlsFromGithub(owner, repository, pathToRoser, accessToken)
            ?.map { getGithubResponse(it.download_url, accessToken) }
            ?.mapNotNull { it.toROS() }

    private fun fetchROSUrlsFromGithub(
        owner: String,
        repository: String,
        pathToRoser: String,
        accessToken: String,
    ): List<ROSDownloadUrlDTO>? =
        getGithubResponse("/$owner/$repository/contents/$pathToRoser", accessToken).rosDownloadUrls()

    internal fun getRosSha(
        owner: String,
        repository: String,
        accessToken: String,
        pathToROS: String,
    ): String? {
        val shaForExistingROS: ShaResponseDTO =
            getGithubResponse("/$owner/$repository/contents/$pathToROS", accessToken).shaReponseDTO()
                ?: return null

        return shaForExistingROS.sha
    }

    internal fun writeToGithub(
        owner: String,
        repository: String,
        path: String,
        accessToken: String,
        writePayload: GithubWritePayload,
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

    private fun ResponseSpec.toROS(): String? = this.bodyToMono<String>().block()

    private fun ResponseSpec.rosDownloadUrls(): List<ROSDownloadUrlDTO>? =
        this.bodyToMono<List<ROSDownloadUrlDTO>>().block()

    private fun ResponseSpec.shaReponseDTO(): ShaResponseDTO? =
        this.bodyToMono<ShaResponseDTO>().block()

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
