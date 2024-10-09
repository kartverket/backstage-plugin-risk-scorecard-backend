package no.risc.risc

import no.risc.exception.exceptions.InvalidAccessTokensException
import no.risc.github.GithubAppConnector
import no.risc.github.GithubRiScIdentifiersResponse
import no.risc.infra.connector.GoogleApiConnector
import no.risc.infra.connector.models.AccessTokens
import no.risc.infra.connector.models.GCPAccessToken
import no.risc.risc.models.*
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/risc")
class RiScController(
    private val riScService: RiScService,
    private val githubAppConnector: GithubAppConnector,
    private val googleApiConnector: GoogleApiConnector,
) {
    @GetMapping("/{repositoryOwner}/{repositoryName}/filenames")
    suspend fun getRiScNames(
        @RequestHeader("GCP-Access-Token") gcpAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
    ): GithubRiScIdentifiersResponse =
        riScService.fetchAllRiScIds(
            repositoryOwner,
            repositoryName,
            getAccessTokens(gcpAccessToken, repositoryName),
        )

    @GetMapping("/{repositoryOwner}/{repositoryName}/all")
    suspend fun getAllRiScsDefault(
        @RequestHeader("GCP-Access-Token") gcpAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
    ): List<RiScContentResultDTO> =
        riScService.fetchAllRiScs(
            repositoryOwner,
            repositoryName,
            getAccessTokens(gcpAccessToken, repositoryName),
            "4",
        )

    @GetMapping("/{repositoryOwner}/{repositoryName}/{latestSupportedVersion}/all")
    suspend fun getAllRiScs(
        @RequestHeader("GCP-Access-Token") gcpAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
        @PathVariable latestSupportedVersion: String,
    ): List<RiScContentResultDTO> =
        riScService.fetchAllRiScs(
            repositoryOwner,
            repositoryName,
            getAccessTokens(gcpAccessToken, repositoryName),
            latestSupportedVersion,
        )

    @PostMapping("/{repositoryOwner}/{repositoryName}")
    suspend fun createNewRiSc(
        @RequestHeader("GCP-Access-Token") gcpAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
        @RequestBody riSc: RiScWrapperObject,
    ): ProcessRiScResultDTO =
        riScService.createRiSc(
            owner = repositoryOwner,
            repository = repositoryName,
            accessTokens = getAccessTokens(gcpAccessToken, repositoryName),
            content = riSc,
        )

    @PostMapping("/{repositoryOwner}/{repositoryName}/initialize")
    suspend fun initializeRiSc(
        @RequestHeader("GCP-Access-Token") gcpAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
        @RequestBody body: InitializeRiScRequestBody,
    ): ResponseEntity<Unit> {
        riScService.scheduleInitializeRiSc(
            owner = repositoryOwner,
            repository = repositoryName,
            gcpProjectId = body.gcpProjectId,
            securityChampionPublicKey = body.publicAgeKey,
            gcpAccessTokenValue = gcpAccessToken
        )
        return ResponseEntity.accepted().build()
    }

    @PostMapping("/{repositoryOwner}/{repositoryName}/initialize/finalize")
    suspend fun commitInitializedRiSc(
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
        @RequestBody body: RiScWrapperObject,

        ) = riScService.finalizeInitializedRiSc(
        owner = repositoryOwner,
        repository = repositoryName,
        content = body,
    )


    @PutMapping("/{repositoryOwner}/{repositoryName}/{id}", produces = ["application/json"])
    suspend fun editRiSc(
        @RequestHeader("GCP-Access-Token") gcpAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable id: String,
        @PathVariable repositoryName: String,
        @RequestBody riSc: RiScWrapperObject,
    ): RiScResult =
        riScService.updateRiSc(
            repositoryOwner,
            repositoryName,
            id,
            riSc,
            getAccessTokens(gcpAccessToken, repositoryName),
        )

    @PostMapping("/{repositoryOwner}/{repositoryName}/publish/{id}", produces = ["application/json"])
    fun sendRiScForPublishing(
        @RequestHeader("GCP-Access-Token") gcpAccessToken: String,
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
                accessTokens = getAccessTokens(gcpAccessToken, repositoryName),
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
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
        @PathVariable riscId: String,
        @RequestBody data: DifferenceRequestBody,
    ): ResponseEntity<DifferenceDTO> {
        val difference =
            riScService.fetchAndDiffRiScs(
                owner = repositoryOwner,
                repository = repositoryName,
                accessTokens = getAccessTokens(gcpAccessToken, repositoryName),
                riScId = riscId,
                headRiSc = data.riSc,
            )

        return ResponseEntity.ok().body(difference)
    }

    private fun getAccessTokens(
        gcpAccessToken: String,
        repositoryName: String,
    ): AccessTokens {
        if (!googleApiConnector.validateAccessToken(gcpAccessToken)) {
            throw InvalidAccessTokensException(
                "Invalid risk scorecard result: ${ProcessingStatus.InvalidAccessTokens.message}",
            )
        }
        val accessTokens =
            AccessTokens(
                githubAppConnector.getAccessTokenFromApp(repositoryName),
                GCPAccessToken(gcpAccessToken),
            )
        return accessTokens
    }
}
