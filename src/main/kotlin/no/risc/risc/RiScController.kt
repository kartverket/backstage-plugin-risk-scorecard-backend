package no.risc.risc

import no.risc.exception.exceptions.InvalidAccessTokensException
import no.risc.github.GithubAppConnector
import no.risc.github.GithubRiScIdentifiersResponse
import no.risc.infra.connector.GoogleApiConnector
import no.risc.infra.connector.models.AccessTokens
import no.risc.infra.connector.models.GCPAccessToken
import no.risc.risc.models.RiScWrapperObject
import no.risc.risc.models.UserInfo
import no.risc.utils.Difference
import no.risc.utils.DifferenceException
import no.risc.utils.diff
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

    @PutMapping("/{repositoryOwner}/{repositoryName}/{id}", produces = ["application/json"])
    suspend fun editRiSc(
        @RequestHeader("GCP-Access-Token") gcpAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable id: String,
        @PathVariable repositoryName: String,
        @RequestBody riSc: RiScWrapperObject,
    ): ProcessRiScResultDTO =
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

    data class DifferenceDTO(
        val status: DifferenceStatus,
        val differenceState: Difference,
        val errorMessage: String = "",
    )

    data class DifferenceRequestBody(
        val riSc: String,
    )

    @PostMapping("/{repositoryOwner}/{repositoryName}/difference/{id}", produces = ["application/json"])
    suspend fun getDifferenceBetweenTwoRiScs(
        @RequestHeader("GCP-Access-Token") gcpAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
        @PathVariable id: String,
        @RequestBody data: DifferenceRequestBody,
    ): ResponseEntity<DifferenceDTO> {
        val defaultRiSc =
            riScService.fetchDefaultRiSc(
                owner = repositoryOwner,
                repository = repositoryName,
                accessTokens = getAccessTokens(gcpAccessToken, repositoryName),
                riScId = id,
            )

        return when (defaultRiSc.status) {
            ContentStatus.Success -> {
                try {
                    val result = diff("${defaultRiSc.riScContent}", data.riSc)
                    ResponseEntity.ok().body(DifferenceDTO(status = DifferenceStatus.Success, differenceState = result))
                } catch (e: DifferenceException) {
                    ResponseEntity.internalServerError().body(
                        DifferenceDTO(status = DifferenceStatus.Failure, Difference(), "${e.message}"),
                    )
                }
            }

            ContentStatus.FileNotFound ->
                ResponseEntity.ok().body(
                    DifferenceDTO(status = DifferenceStatus.DefaultNotFound, differenceState = Difference(), "File not found"),
                )
            ContentStatus.DecryptionFailed ->
                ResponseEntity.ok().body(
                    DifferenceDTO(status = DifferenceStatus.Failure, differenceState = Difference(), "Decryption failed"),
                )
            ContentStatus.Failure ->
                ResponseEntity.ok().body(
                    DifferenceDTO(status = DifferenceStatus.Failure, differenceState = Difference(), "Unknown failure"),
                )
        }
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
