package no.risc.exception.exceptions

import no.risc.infra.connector.models.GitHubPermission
import no.risc.risc.models.ContentStatus
import no.risc.risc.models.ProcessRiScResultDTO
import no.risc.risc.models.ProcessingStatus
import no.risc.risc.models.RiScContentResultDTO

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
