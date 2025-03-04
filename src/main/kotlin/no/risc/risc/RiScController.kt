package no.risc.risc

import no.risc.github.GitHubAppService
import no.risc.github.GithubConnector
import no.risc.infra.connector.models.AccessTokens
import no.risc.infra.connector.models.GCPAccessToken
import no.risc.infra.connector.models.GithubAccessToken
import no.risc.risc.models.DifferenceDTO
import no.risc.risc.models.DifferenceRequestBody
import no.risc.risc.models.RiScWrapperObject
import no.risc.risc.models.UserInfo
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/risc")
class RiScController(
    private val riScService: RiScService,
    private val githubConnector: GithubConnector,
    private val gitHubAppService: GitHubAppService,
) {
    @GetMapping("/{repositoryOwner}/{repositoryName}/all")
    suspend fun getAllRiScsDefault(
        @RequestHeader("GCP-Access-Token") gcpAccessToken: String,
        @RequestHeader("GitHub-Access-Token") gitHubAccessToken: String? = null,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
    ): List<RiScContentResultDTO> =
        riScService.fetchAllRiScs(
            repositoryOwner,
            repositoryName,
            AccessTokens(
                gcpAccessToken = GCPAccessToken(gcpAccessToken),
                githubAccessToken = gitHubAppService.getGitHubAccessToken(gitHubAccessToken),
            ),
            "4",
        )

    @GetMapping("/{repositoryOwner}/{repositoryName}/{latestSupportedVersion}/all")
    suspend fun getAllRiScs(
        @RequestHeader("GCP-Access-Token") gcpAccessToken: String,
        @RequestHeader("GitHub-Access-Token") gitHubAccessToken: String? = null,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
        @PathVariable latestSupportedVersion: String,
    ): List<RiScContentResultDTO> {
        val result =
            riScService.fetchAllRiScs(
                repositoryOwner,
                repositoryName,
                AccessTokens(
                    gcpAccessToken = GCPAccessToken(gcpAccessToken),
                    githubAccessToken = gitHubAppService.getGitHubAccessToken(gitHubAccessToken),
                ),
                latestSupportedVersion,
            )
        return result
    }

    @PostMapping("/{repositoryOwner}/{repositoryName}")
    suspend fun createNewRiSc(
        @RequestHeader("GCP-Access-Token") gcpAccessToken: String,
        @RequestHeader("GitHub-Access-Token") gitHubAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
        @RequestBody riSc: RiScWrapperObject,
        @RequestParam generateDefault: Boolean = false,
    ): CreateRiScResultDTO =
        riScService.createRiSc(
            owner = repositoryOwner,
            repository = repositoryName,
            accessTokens =
                AccessTokens(
                    gcpAccessToken = GCPAccessToken(gcpAccessToken),
                    githubAccessToken = GithubAccessToken(gitHubAccessToken),
                ),
            content = riSc,
            defaultBranch = githubConnector.fetchDefaultBranch(repositoryOwner, repositoryName, gitHubAccessToken),
            generateDefault = generateDefault,
        )

    @PutMapping("/{repositoryOwner}/{repositoryName}/{id}", produces = ["application/json"])
    suspend fun editRiSc(
        @RequestHeader("GCP-Access-Token") gcpAccessToken: String,
        @RequestHeader("GitHub-Access-Token") gitHubAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable id: String,
        @PathVariable repositoryName: String,
        @RequestBody riSc: RiScWrapperObject,
    ) =
        riScService.updateRiSc(
            repositoryOwner,
            repositoryName,
            id,
            riSc,
            AccessTokens(
            gcpAccessToken = GCPAccessToken(gcpAccessToken),
            githubAccessToken = GithubAccessToken(gitHubAccessToken),
        ),
            githubConnector.fetchDefaultBranch(repositoryOwner, repositoryName, gitHubAccessToken),
        )

    @PostMapping("/{repositoryOwner}/{repositoryName}/publish/{id}", produces = ["application/json"])
    fun sendRiScForPublishing(
        @RequestHeader("GCP-Access-Token") gcpAccessToken: String,
        @RequestHeader("GitHub-Access-Token") gitHubAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
        @PathVariable id: String,
        @RequestBody userInfo: UserInfo,
    ): ResponseEntity<PublishRiScResultDTO> {
        val result =
            riScService.publishRiSc(
                owner = repositoryOwner,
                repository = repositoryName,
                riScId = id,
                accessTokens =
                    AccessTokens(
                        gcpAccessToken = GCPAccessToken(gcpAccessToken),
                        githubAccessToken = GithubAccessToken(gitHubAccessToken),
                    ),
                userInfo = userInfo,
            )

        return when (result.status) {
            ProcessingStatus.CreatedPullRequest -> ResponseEntity.ok().body(result)
            else -> ResponseEntity.internalServerError().body(result)
        }
    }

    @PostMapping("/{repositoryOwner}/{repositoryName}/{riscId}/difference", produces = ["application/json"])
    suspend fun getDifferenceBetweenTwoRiScs(
        @RequestHeader("GCP-Access-Token") gcpAccessToken: String,
        @RequestHeader("GitHub-Access-Token") gitHubAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
        @PathVariable riscId: String,
        @RequestBody data: DifferenceRequestBody,
    ): ResponseEntity<DifferenceDTO> {
        val difference =
            riScService.fetchAndDiffRiScs(
                owner = repositoryOwner,
                repository = repositoryName,
                accessTokens =
                    AccessTokens(
                        gcpAccessToken = GCPAccessToken(gcpAccessToken),
                        githubAccessToken = GithubAccessToken(gitHubAccessToken),
                    ),
                riScId = riscId,
                headRiSc = data.riSc,
            )

        return ResponseEntity.ok().body(difference)
    }
}
