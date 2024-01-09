package no.kvros.github

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import no.kvros.client.Connector
import org.springframework.stereotype.Component

val mapper = jacksonObjectMapper()

@Component
class GithubConnector : Connector("https://api.github.com/repos") {
    fun fetchROSes(
        owner: String,
        repository: String,
        pathToRoser: String,
        accessToken: String,
    ): List<String>? =
        fetchROSUrls(owner, repository, pathToRoser, accessToken)
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

    private fun fetchROSUrls(
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
