package no.risc.exception.exceptions

import no.risc.infra.connector.models.GitHubPermission
import no.risc.risc.ContentStatus
import no.risc.risc.ProcessRiScResultDTO
import no.risc.risc.ProcessingStatus
import no.risc.risc.RiScContentResultDTO

data class AccessTokenValidationFailedException(
    val permissionNeeded: GitHubPermission,
    val response: Any =
        when (permissionNeeded) {
            GitHubPermission.READ ->
                RiScContentResultDTO(
                    riScId = "",
                    status = ContentStatus.Failure,
                    riScStatus = null,
                    riScContent = null,
                    pullRequestUrl = null,
                )

            GitHubPermission.WRITE ->
                ProcessRiScResultDTO(
                    riScId = "",
                    status = ProcessingStatus.AccessTokensValidationFailure,
                    statusMessage = "An error occurred when validation access tokens.",
                )
        },
    override val message: String,
) : Exception()
