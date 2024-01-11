package no.kvros.ros

import no.kvros.infra.connector.WebClientConnector
import no.kvros.ros.models.ROSDownloadUrl
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import java.util.UUID

data class GithubWritePayload(
    val message: String,
    val content: String,
    // val sha: String - denne må brukes når vi skal oppdatere noe -> kommer snart
) {
    fun toContentBody(): String = "{\"message\":\"$message\", \"content\":\"$content\"}"
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
    ): List<ROSDownloadUrl>? = getGithubResponse("/$owner/$repository/contents/$pathToRoser", accessToken).toROSDownloadUrls()

    internal fun getRosSha(
        owner: String,
        repository: String,
        accessToken: String,
        writePayload: GithubWritePayload,
        rosFilePath: String = "./sikkerhet/.ros2.yaml",
    ): String {
        return ""
    }

    internal fun writeToGithub(
        owner: String,
        repository: String,
        accessToken: String,
        writePayload: GithubWritePayload,
        rosFilePath: String = ".sikkerhet/ros/${UUID.randomUUID()}.ros.yaml",
    ): String? {
        val uri = "/$owner/$repository/contents/$rosFilePath"

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

    private fun ResponseSpec.toROSDownloadUrls(): List<ROSDownloadUrl>? = this.bodyToMono<List<ROSDownloadUrl>>().block()

    private fun getGithubResponse(
        uri: String,
        accessToken: String,
    ): ResponseSpec =
        webClient.get()
            .uri(uri)
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "token $accessToken")
            .retrieve()
}
