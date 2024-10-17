package no.risc.risc

import no.risc.exception.exceptions.InvalidAccessTokensException
import no.risc.exception.exceptions.NoReadAccessToRepositoryException
import no.risc.exception.exceptions.NoWriteAccessToRepositoryException
import no.risc.github.GithubConnector
import no.risc.infra.connector.GoogleApiConnector
import no.risc.infra.connector.models.AccessTokens
import no.risc.infra.connector.models.GCPAccessToken
import no.risc.infra.connector.models.GitHubPermission
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
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/risc")
class RiScController(
    private val riScService: RiScService,
    private val googleApiConnector: GoogleApiConnector,
    private val githubConnector: GithubConnector,
) {
    @GetMapping("/{repositoryOwner}/{repositoryName}/all")
    suspend fun getAllRiScsDefault(
        @RequestHeader("GCP-Access-Token") gcpAccessToken: String,
        @RequestHeader("GitHub-Access-Token") gitHubAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
    ): List<RiScContentResultDTO> =
        riScService.fetchAllRiScs(
            repositoryOwner,
            repositoryName,
            getAccessTokens(
                gcpAccessToken = gcpAccessToken,
                gitHubAccessToken = gitHubAccessToken,
                repositoryOwner = repositoryOwner,
                repositoryName = repositoryName,
                gitHubPermissionNeeded = GitHubPermission.READ,
            ),
            "4",
        )

    @GetMapping("/{repositoryOwner}/{repositoryName}/{latestSupportedVersion}/all")
    suspend fun getAllRiScs(
        @RequestHeader("GCP-Access-Token") gcpAccessToken: String,
        @RequestHeader("GitHub-Access-Token") gitHubAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
        @PathVariable latestSupportedVersion: String,
    ): List<RiScContentResultDTO> =
        riScService.fetchAllRiScs(
            repositoryOwner,
            repositoryName,
            getAccessTokens(
                gcpAccessToken = gcpAccessToken,
                gitHubAccessToken = gitHubAccessToken,
                repositoryOwner = repositoryOwner,
                repositoryName = repositoryName,
                gitHubPermissionNeeded = GitHubPermission.READ,
            ),
            latestSupportedVersion,
        )

    @PostMapping("/{repositoryOwner}/{repositoryName}")
    suspend fun createNewRiSc(
        @RequestHeader("GCP-Access-Token") gcpAccessToken: String,
        @RequestHeader("GitHub-Access-Token") gitHubAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
        @RequestBody riSc: RiScWrapperObject,
    ): ProcessRiScResultDTO =
        riScService.createRiSc(
            owner = repositoryOwner,
            repository = repositoryName,
            accessTokens =
                getAccessTokens(
                    gcpAccessToken = gcpAccessToken,
                    gitHubAccessToken = gitHubAccessToken,
                    repositoryOwner = repositoryOwner,
                    repositoryName = repositoryName,
                    gitHubPermissionNeeded = GitHubPermission.WRITE,
                ),
            content = riSc,
        )

    @PutMapping("/{repositoryOwner}/{repositoryName}/{id}", produces = ["application/json"])
    suspend fun editRiSc(
        @RequestHeader("GCP-Access-Token") gcpAccessToken: String,
        @RequestHeader("GitHub-Access-Token") gitHubAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable id: String,
        @PathVariable repositoryName: String,
        @RequestBody riSc: RiScWrapperObject,
    ) = riScService.updateRiSc(
        repositoryOwner,
        repositoryName,
        id,
        riSc,
        getAccessTokens(
            gcpAccessToken,
            gitHubAccessToken,
            repositoryOwner,
            repositoryName,
            GitHubPermission.WRITE,
        ),
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
                    getAccessTokens(
                        gcpAccessToken = gcpAccessToken,
                        gitHubAccessToken = gitHubAccessToken,
                        repositoryOwner = repositoryOwner,
                        repositoryName = repositoryName,
                        gitHubPermissionNeeded = GitHubPermission.WRITE,
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
                    getAccessTokens(
                        gcpAccessToken = gcpAccessToken,
                        gitHubAccessToken = gitHubAccessToken,
                        repositoryOwner = repositoryOwner,
                        repositoryName = repositoryName,
                        gitHubPermissionNeeded = GitHubPermission.READ,
                    ),
                riScId = riscId,
                headRiSc = data.riSc,
            )

        return ResponseEntity.ok().body(difference)
    }

    private fun getAccessTokens(
        gcpAccessToken: String,
        gitHubAccessToken: String,
        repositoryOwner: String,
        repositoryName: String,
        gitHubPermissionNeeded: GitHubPermission,
    ): AccessTokens {
        val gitHubPermissions =
            githubConnector.getRepositoryPermissions(gitHubAccessToken, repositoryOwner, repositoryName)
        if (gitHubPermissionNeeded !in gitHubPermissions) {
            when (gitHubPermissionNeeded) {
                GitHubPermission.READ -> throw NoReadAccessToRepositoryException(
                    listOf(
                        RiScContentResultDTO(
                            riScId = "",
                            status = ContentStatus.NoReadAccess,
                            riScStatus = null,
                            riScContent = null,
                            pullRequestUrl = null,
                        ),
                    ),
                    "Access denied. No read permission on $repositoryOwner/$repositoryName",
                )

                GitHubPermission.WRITE -> throw NoWriteAccessToRepositoryException(
                    ProcessRiScResultDTO(
                        riScId = "",
                        status = ProcessingStatus.NoWriteAccessToRepository,
                        statusMessage = "Access denied. No write permission on $repositoryOwner/$repositoryName",
                    ),
                    "Access denied. No write permission on $repositoryOwner/$repositoryName",
                )
            }
        }
        if (!googleApiConnector.validateAccessToken(gcpAccessToken)) {
            throw InvalidAccessTokensException(
                "Invalid risk scorecard result: ${ProcessingStatus.InvalidAccessTokens.message}",
            )
        }
        val accessTokens =
            AccessTokens(
                GithubAccessToken(gitHubAccessToken),
                GCPAccessToken(gcpAccessToken),
            )
        return accessTokens
    }
}
