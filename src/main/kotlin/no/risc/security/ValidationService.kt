package no.risc.security

import no.risc.exception.exceptions.AccessTokenValidationFailedException
import no.risc.exception.exceptions.InvalidGcpAccessTokenException
import no.risc.exception.exceptions.InvalidGithubAccessTokenException
import no.risc.exception.exceptions.RepositoryAccessException
import no.risc.github.GithubConnector
import no.risc.google.GoogleServiceIntegration
import no.risc.risc.models.ProcessingStatus
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClientResponseException

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
            } catch (e: WebClientResponseException.Unauthorized) {
                throw InvalidGithubAccessTokenException(
                    message = "Invalid GitHub access token for $repositoryOwner/$repositoryName",
                    cause = e,
                )
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
            throw InvalidGcpAccessTokenException(
                "Invalid GCP access token for $repositoryOwner/$repositoryName",
            )
        }
    }
}
