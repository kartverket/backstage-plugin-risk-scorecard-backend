package no.risc.exception.exceptions

import no.risc.infra.connector.models.GitHubPermission
import no.risc.risc.ContentStatus
import no.risc.risc.ProcessRiScResultDTO
import no.risc.risc.ProcessingStatus
import no.risc.risc.RiScContentResultDTO

data class RepositoryAccessException(
    val permissionNeeded: GitHubPermission,
    val response: Any =
        when (permissionNeeded) {
            GitHubPermission.READ ->
                RiScContentResultDTO(
                    riScId = "",
                    status = ContentStatus.NoReadAccess,
                    riScStatus = null,
                    riScContent = null,
                    pullRequestUrl = null,
                    numOfGeneralCommitsBehind = null,
                )

            GitHubPermission.WRITE ->
                ProcessRiScResultDTO(
                    riScId = "",
                    status = ProcessingStatus.NoWriteAccessToRepository,
                    statusMessage = "Access denied: No write permission",
                )
        },
    override val message: String?,
) : Exception()
