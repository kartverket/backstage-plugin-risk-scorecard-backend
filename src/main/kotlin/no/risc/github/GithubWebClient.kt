package no.risc.github

import no.risc.github.models.GithubPatchPayload
import no.risc.github.models.GithubPostPayload
import no.risc.github.models.GithubPutPayload
import no.risc.infra.connector.WebClientConnector
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient.ResponseSpec
import reactor.core.publisher.Mono

@Component
class GithubWebClient : WebClientConnector("https://api.github.com/repos") {
    fun get(
        uri: String,
        accessToken: String,
    ): ResponseSpec =
        webClient
            .get()
            .uri(uri)
            .header("Accept", "application/vnd.github.json")
            .header("Authorization", "token $accessToken")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .retrieve()

    fun put(
        uri: String,
        accessToken: String,
        githubPutPayload: GithubPutPayload,
    ) = webClient
        .put()
        .uri(uri)
        .header("Accept", "application/vnd.github+json")
        .header("Authorization", "token $accessToken")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .header("Content-Type", "application/json")
        .body(Mono.just(githubPutPayload.toContentBody()), String::class.java)
        .retrieve()

    fun post(
        uri: String,
        accessToken: String,
        githubPostPayload: GithubPostPayload,
    ) = webClient
        .post()
        .uri(uri)
        .header("Accept", "application/vnd.github+json")
        .header("Authorization", "token $accessToken")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .header("Content-Type", "application/json")
        .body(Mono.just(githubPostPayload.toContentBody()), String::class.java)
        .retrieve()

    fun patch(
        uri: String,
        accessToken: String,
        githubPatchPayload: GithubPatchPayload,
    ) = webClient
        .patch()
        .uri(uri)
        .header("Accept", "application/vnd.github+json")
        .header("Authorization", "token $accessToken")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .header("Content-Type", "application/json")
        .body(Mono.just(githubPatchPayload), String::class.java)
        .retrieve()
}
