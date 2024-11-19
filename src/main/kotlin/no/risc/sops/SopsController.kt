package no.risc.sops

import no.risc.infra.connector.models.AccessTokens
import no.risc.infra.connector.models.GCPAccessToken
import no.risc.infra.connector.models.GithubAccessToken
import no.risc.sops.model.GetSopsConfigResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/sops")
class SopsController(
    private val sopsService: SopsService,
) {
    @GetMapping("/{repositoryOwner}/{repositoryName}")
    fun getSopsConfig(
        @RequestHeader("GitHub-Access-Token") gitHubAccessToken: String,
        @RequestHeader("GCP-Access-Token") gcpAccessToken: String,
        @PathVariable("repositoryOwner") repositoryOwner: String,
        @PathVariable("repositoryName") repositoryName: String,
    ): GetSopsConfigResponse =
        sopsService.getSopsConfig(
            repositoryOwner,
            repositoryName,
            AccessTokens(
                githubAccessToken = GithubAccessToken(gitHubAccessToken),
                gcpAccessToken = GCPAccessToken(gcpAccessToken),
            ),
        )
}
