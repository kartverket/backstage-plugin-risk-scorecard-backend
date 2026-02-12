package no.risc.initRiSc

import io.swagger.v3.oas.annotations.tags.Tag
import no.risc.github.GitHubAppService
import no.risc.infra.connector.models.AccessTokens
import no.risc.infra.connector.models.GCPAccessToken
import no.risc.initRiSc.model.RiScTypeDescriptor
import no.risc.risc.models.RiSc5X
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/initrisc")
@Tag(name = "initrisc", description = "Init Risc endpoints")
class InitRiScController(
    private val initRiScService: InitRiScService,
    private val gitHubAppService: GitHubAppService,
) {
    @GetMapping("", "/descriptors")
    suspend fun getAllDefaultRiScTypeDescriptors(
        @RequestHeader("GCP-Access-Token") gcpAccessToken: String,
        @RequestHeader("GitHub-Access-Token") gitHubAccessToken: String? = null,
    ): List<RiScTypeDescriptor> =
        initRiScService.getInitRiScDescriptors(
            AccessTokens(
                gcpAccessToken = GCPAccessToken(gcpAccessToken),
                githubAccessToken = gitHubAppService.getGitHubAccessToken(gitHubAccessToken),
            ),
        )

    @GetMapping("/{initRiScId}")
    suspend fun getInitRiSc(
        @PathVariable initRiScId: String,
        @RequestHeader("GCP-Access-Token") gcpAccessToken: String,
        @RequestHeader("GitHub-Access-Token") gitHubAccessToken: String? = null,
    ): String {
        val initRiSc =
            initRiScService.getInitRiSc(
                initRiScId,
                "{}",
                AccessTokens(
                    gcpAccessToken = GCPAccessToken(gcpAccessToken),
                    githubAccessToken = gitHubAppService.getGitHubAccessToken(gitHubAccessToken),
                ),
            )

        return initRiSc
    }
}
