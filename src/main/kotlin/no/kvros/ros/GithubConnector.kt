package no.kvros.ros

import no.kvros.infra.connector.WebClientConnector
import no.kvros.ros.models.ROSDownloadUrl
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec
import org.springframework.web.reactive.function.client.bodyToMono

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
    ): List<ROSDownloadUrl>? =
        getGithubResponse("/$owner/$repository/contents/$pathToRoser", accessToken).toROSDownloadUrls()

    private fun ResponseSpec.toROS(): String? = this.bodyToMono<String>().block()

    private fun ResponseSpec.toROSDownloadUrls(): List<ROSDownloadUrl>? =
        this.bodyToMono<List<ROSDownloadUrl>>().block()

    private fun getGithubResponse(uri: String, accessToken: String): ResponseSpec =
        webClient.get()
            .uri(uri)
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "token $accessToken")
            .retrieve()
}
