package no.risc.risc

import no.risc.github.GitHubAppService
import no.risc.github.GithubConnector
import no.risc.infra.connector.models.AccessTokens
import no.risc.infra.connector.models.GCPAccessToken
import no.risc.infra.connector.models.GithubAccessToken
import no.risc.risc.models.CreateRiScResultDTO
import no.risc.risc.models.DeleteRiScResultDTO
import no.risc.risc.models.DifferenceDTO
import no.risc.risc.models.DifferenceRequestBody
import no.risc.risc.models.PublishRiScResultDTO
import no.risc.risc.models.RiScContentResultDTO
import no.risc.risc.models.RiScResult
import no.risc.risc.models.RiScWrapperObject
import no.risc.risc.models.UserInfo
import no.risc.slack.SlackService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
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
    private val slackService: SlackService,
) {
    @GetMapping("/{repositoryOwner}/{repositoryName}/all")
    suspend fun getAllRiScsDefault(
        @RequestHeader("GCP-Access-Token") gcpAccessToken: String,
        @RequestHeader("GitHub-Access-Token") gitHubAccessToken: String? = null,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
    ): List<RiScContentResultDTO> =
        riScService.fetchAllRiScsV2(
            owner = repositoryOwner,
            repository = repositoryName,
            accessTokens =
                AccessTokens(
                    gcpAccessToken = GCPAccessToken(gcpAccessToken),
                    githubAccessToken = gitHubAppService.getGitHubAccessToken(gitHubAccessToken),
                ),
            latestSupportedVersion = "5.0",
        )

    @GetMapping("/{repositoryOwner}/{repositoryName}/{latestSupportedVersion}/all")
    suspend fun getAllRiScs(
        @RequestHeader("GCP-Access-Token") gcpAccessToken: String,
        @RequestHeader("GitHub-Access-Token") gitHubAccessToken: String? = null,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
        @PathVariable latestSupportedVersion: String,
    ): List<RiScContentResultDTO> {
        val result = riScService.fetchAllRiScsV2(
            owner = repositoryOwner,
            repository = repositoryName,
            accessTokens =
                AccessTokens(
                    gcpAccessToken = GCPAccessToken(gcpAccessToken),
                    githubAccessToken = gitHubAppService.getGitHubAccessToken(gitHubAccessToken),
                ),
            latestSupportedVersion = latestSupportedVersion
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
    ): CreateRiScResultDTO {
        val createRiscResultDTO =
            riScService.createRiSc(
                owner = repositoryOwner,
                repository = repositoryName,
                accessTokens =
                    AccessTokens(
                        gcpAccessToken = GCPAccessToken(gcpAccessToken),
                        githubAccessToken = GithubAccessToken(gitHubAccessToken),
                    ),
                content = riSc,
                defaultBranch =
                    githubConnector
                        .fetchRepositoryInfo(
                            repositoryOwner = repositoryOwner,
                            repositoryName = repositoryName,
                            gitHubAccessToken = gitHubAccessToken,
                        ).defaultBranch,
                generateDefault = generateDefault,
            )
        if (createRiscResultDTO.riScContent != null) {
            riScService.uploadRiScToRosa(createRiscResultDTO.riScId, repositoryName, createRiscResultDTO.riScContent)
        }
        return createRiscResultDTO
    }

    @PutMapping("/{repositoryOwner}/{repositoryName}/{id}", produces = ["application/json"])
    suspend fun editRiSc(
        @RequestHeader("GCP-Access-Token") gcpAccessToken: String,
        @RequestHeader("GitHub-Access-Token") gitHubAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable id: String,
        @PathVariable repositoryName: String,
        @RequestBody riSc: RiScWrapperObject,
    ): RiScResult {
        val riScResult =
            riScService.updateRiSc(
                owner = repositoryOwner,
                repository = repositoryName,
                riScId = id,
                content = riSc,
                accessTokens =
                    AccessTokens(
                        gcpAccessToken = GCPAccessToken(gcpAccessToken),
                        githubAccessToken = GithubAccessToken(gitHubAccessToken),
                    ),
                defaultBranch =
                    githubConnector
                        .fetchRepositoryInfo(
                            repositoryOwner = repositoryOwner,
                            repositoryName = repositoryName,
                            gitHubAccessToken = gitHubAccessToken,
                        ).defaultBranch,
            )
        riScService.uploadRiScToRosa(
            id,
            repositoryName,
            riSc.riSc,
        )
        return riScResult
    }

    @DeleteMapping("/{repositoryOwner}/{repositoryName}/{id}", produces = ["application/json"])
    suspend fun deleteRiSc(
        @RequestHeader("GCP-Access-Token") gcpAccessToken: String,
        @RequestHeader("GitHub-Access-Token") gitHubAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
        @PathVariable id: String,
    ): DeleteRiScResultDTO {
        val deleteRiscResultDTO =
            riScService.deleteRiSc(
                owner = repositoryOwner,
                repository = repositoryName,
                riScId = id,
                accessTokens =
                    AccessTokens(
                        gcpAccessToken = GCPAccessToken(gcpAccessToken),
                        githubAccessToken = GithubAccessToken(gitHubAccessToken),
                    ),
            )
        riScService.deleteRiscFromRosa(riScId = id)
        return deleteRiscResultDTO
    }

    @PostMapping("/{repositoryOwner}/{repositoryName}/publish/{id}", produces = ["application/json"])
    suspend fun sendRiScForPublishing(
        @RequestHeader("GCP-Access-Token") gcpAccessToken: String,
        @RequestHeader("GitHub-Access-Token") gitHubAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
        @PathVariable id: String,
        @RequestBody userInfo: UserInfo,
    ): PublishRiScResultDTO =
        riScService.publishRiSc(
            owner = repositoryOwner,
            repository = repositoryName,
            riScId = id,
            gitHubAccessToken = gitHubAccessToken,
            userInfo = userInfo,
        )

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
            riScService.fetchAndDiffRiSc(
                owner = repositoryOwner,
                repository = repositoryName,
                accessTokens =
                    AccessTokens(
                        gcpAccessToken = GCPAccessToken(gcpAccessToken),
                        githubAccessToken = GithubAccessToken(gitHubAccessToken),
                    ),
                riScId = riscId,
                draftRiScContent = data.riSc,
            )

        return ResponseEntity.ok().body(difference)
    }

    @PostMapping("/{repositoryOwner}/{repositoryName}/feedback", produces = ["application/json"])
    suspend fun sendFeedback(
        @RequestBody feedbackMessage: String,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
    ): ResponseEntity<Map<String, String>> =
        try {
            slackService.sendFeedback(feedbackMessage)
            ResponseEntity.ok(emptyMap())
        } catch (e: Exception) {
            ResponseEntity.status(500).body(mapOf("error" to "Failed to send feedback to Slack"))
        }
}
