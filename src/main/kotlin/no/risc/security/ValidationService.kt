package no.risc.security

import no.risc.exception.exceptions.InvalidAccessTokensException
import no.risc.exception.exceptions.NoReadAccessToRepositoryException
import no.risc.exception.exceptions.NoWriteAccessToRepositoryException
import no.risc.github.GithubConnector
import no.risc.infra.connector.GoogleApiConnector
import no.risc.infra.connector.models.GitHubPermission
import no.risc.risc.ContentStatus
import no.risc.risc.ProcessRiScResultDTO
import no.risc.risc.ProcessingStatus
import no.risc.risc.RiScContentResultDTO
import org.springframework.stereotype.Service

@Service
class ValidationService(
    private val githubConnector: GithubConnector,
    private val googleApiConnector: GoogleApiConnector,
) {
    fun validateAccessTokens(
        gcpAccessToken: String,
        gitHubAccessToken: String,
        gitHubPermissionNeeded: GitHubPermission,
        repositoryOwner: String,
        repositoryName: String,
    ) {
        val repositoryInfo =
            githubConnector.fetchRepositoryInfo(gitHubAccessToken, repositoryOwner, repositoryName)
        if (gitHubPermissionNeeded !in repositoryInfo.permissions) {
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
    }
}
