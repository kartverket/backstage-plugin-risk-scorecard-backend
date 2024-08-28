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

    @PostMapping("/{repositoryOwner}/{repositoryName}/{riscId}/difference", produces = ["application/json"])
    suspend fun getDifferenceBetweenTwoRiScs(
        @RequestHeader("GCP-Access-Token") gcpAccessToken: String,
        @PathVariable repositoryOwner: String,
        @PathVariable repositoryName: String,
        @PathVariable riscId: String,
        @RequestBody data: DifferenceRequestBody,
    ): ResponseEntity<DifferenceDTO> {
        val defaultRiSc =
            riScService.fetchDefaultRiSc(
                owner = repositoryOwner,
                repository = repositoryName,
                accessTokens = getAccessTokens(gcpAccessToken, repositoryName),
                riScId = riscId,
            )

        class InternDifference(
            val status: DifferenceStatus,
            val differenceState: Difference,
            val errorMessage: String = "",
        ) {
            fun toDTO(): DifferenceDTO {
                return DifferenceDTO(status = status, differenceState = differenceState, errorMessage = errorMessage)
            }
        }

        val result: InternDifference =
            when (defaultRiSc.status) {
                ContentStatus.Success -> {
                    try {
                        InternDifference(
                            status = DifferenceStatus.Success,
                            differenceState = diff("${defaultRiSc.riScContent}", data.riSc),
                            "",
                        )
                    } catch (e: DifferenceException) {
                        InternDifference(
                            status = DifferenceStatus.JsonFailure,
                            Difference(),
                            "${e.message}",
                        )
                    }
                }

                ContentStatus.FileNotFound ->
                    InternDifference(
                        status = DifferenceStatus.GithubFailure,
                        differenceState = Difference(),
                        "Encountered Github problem: File not found",
                    )
                ContentStatus.DecryptionFailed ->
                    InternDifference(
                        status = DifferenceStatus.DecryptionFailure,
                        differenceState = Difference(),
                        "Encountered ROS problem: Could not decrypt content",
                    )
                ContentStatus.Failure ->
                    InternDifference(
                        status = DifferenceStatus.GithubFailure,
                        differenceState = Difference(),
                        "Encountered Github problem: Github failure",
                    )
            }

        return ResponseEntity.ok().body(result.toDTO())
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
