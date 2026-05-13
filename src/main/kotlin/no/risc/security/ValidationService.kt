package no.risc.security

import no.risc.exception.exceptions.AccessTokenValidationFailedException
import no.risc.exception.exceptions.InvalidAccessTokensException
import no.risc.exception.exceptions.RepositoryAccessException
import no.risc.github.GithubConnector
import no.risc.google.GoogleServiceIntegration
import no.risc.risc.models.ProcessingStatus
import org.springframework.stereotype.Service

@Service
class ValidationService(
    private val githubConnector: GithubConnector,
    private val googleServiceIntegration: GoogleServiceIntegration,
) {
    suspend fun validateAccessTokens(
        gcpAccessToken: String,
        gitHubAccessToken: String,
        repositoryOwner: String,
        repositoryName: String,
    ) {
        val repositoryInfo =
            try {
                githubConnector.fetchRepositoryInfo(gitHubAccessToken, repositoryOwner, repositoryName)
            } catch (e: Exception) {
                throw AccessTokenValidationFailedException(
                    message =
                        "An error occurred when fetching repository info for " +
                            "$repositoryOwner/$repositoryName during access token validation",
                    cause = e,
                )
            }
        if (!repositoryInfo.hasWriteAccess) {
            throw RepositoryAccessException(
                message = "Access denied: No write-access on $repositoryOwner/$repositoryName",
            )
        }
        if (!googleServiceIntegration.validateAccessToken(gcpAccessToken)) {
            throw InvalidAccessTokensException(
                "Invalid risk scorecard result: ${ProcessingStatus.InvalidAccessTokens.message}",
            )
        }
    }
}
