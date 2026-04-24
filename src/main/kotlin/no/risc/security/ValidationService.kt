package no.risc.security

import no.risc.exception.exceptions.AccessTokenValidationFailedException
import no.risc.exception.exceptions.InvalidAccessTokensException
import no.risc.exception.exceptions.RepositoryAccessException
import no.risc.github.GithubConnectorPort
import no.risc.google.GoogleServiceIntegration
import no.risc.infra.connector.models.GitHubPermission
import no.risc.risc.models.ProcessingStatus
import org.springframework.stereotype.Service

@Service
class ValidationService(
    private val githubConnector: GithubConnectorPort,
    private val googleServiceIntegration: GoogleServiceIntegration,
) {
    suspend fun validateAccessTokens(
        gcpAccessToken: String,
        gitHubAccessToken: String,
        gitHubPermissionNeeded: GitHubPermission,
        repositoryOwner: String,
        repositoryName: String,
    ) {
        val repositoryInfo =
            try {
                githubConnector.fetchRepositoryInfo(gitHubAccessToken, repositoryOwner, repositoryName)
            } catch (e: Exception) {
                throw AccessTokenValidationFailedException(
                    permissionNeeded = gitHubPermissionNeeded,
                    message =
                        "An error occurred when fetching repository info for " +
                            "$repositoryOwner/$repositoryName during access token validation",
                )
            }
        if (gitHubPermissionNeeded !in repositoryInfo.permissions) {
            throw RepositoryAccessException(
                permissionNeeded = gitHubPermissionNeeded,
                message = "Access denied: No ${gitHubPermissionNeeded.name.lowercase()}-access on $repositoryOwner/$repositoryName",
            )
        }
        if (!googleServiceIntegration.validateAccessToken(gcpAccessToken)) {
            throw InvalidAccessTokensException(
                "Invalid risk scorecard result: ${ProcessingStatus.InvalidAccessTokens.message}",
            )
        }
    }
}
