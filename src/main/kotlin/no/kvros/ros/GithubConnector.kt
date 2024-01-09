package no.kvros.ros

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import no.kvros.ros.models.ROSDownloadUrls
import no.kvros.utils.connector.Connector
import org.springframework.stereotype.Component

val mapper = jacksonObjectMapper()

@Component
class GithubConnector : Connector("https://api.github.com/repos") {
    fun fetchROSesFromGithub(
        owner: String,
        repository: String,
        pathToRoser: String,
        accessToken: String,
    ): List<String>? =
        fetchROSUrlsFromGithub(owner, repository, pathToRoser, accessToken)
            ?.map {
                webClient
                    .get()
                    .uri(it.download_url)
                    .header("Accept", "application/vnd.github+json")
                    .header("Authorization", "token $accessToken")
                    .retrieve()
                    .bodyToMono(typeReference<String>())
            }
            ?.mapNotNull { it.block() }

    private fun fetchROSUrlsFromGithub(
        owner: String,
        repository: String,
        pathToRoser: String,
        accessToken: String,
    ): List<ROSDownloadUrls>? =
        webClient
            .get()
            .uri("/$owner/$repository/contents/$pathToRoser")
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "token $accessToken")
            .retrieve()
            .bodyToMono(typeReference<String>())
            .block()?.let {
                mapper.readValue<List<ROSDownloadUrls>>(
                    it,
                )
            }
}
