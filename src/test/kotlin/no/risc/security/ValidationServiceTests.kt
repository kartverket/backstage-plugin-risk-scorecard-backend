package no.risc.security

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.risc.exception.exceptions.AccessTokenValidationFailedException
import no.risc.exception.exceptions.InvalidAccessTokensException
import no.risc.exception.exceptions.PermissionDeniedOnGitHubException
import no.risc.exception.exceptions.RepositoryAccessException
import no.risc.github.GithubConnector
import no.risc.google.GoogleServiceIntegration
import no.risc.infra.connector.models.GitHubPermission
import no.risc.infra.connector.models.RepositoryInfo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException

class ValidationServiceTests {
    private lateinit var validationService: ValidationService
    private lateinit var githubConnector: GithubConnector
    private lateinit var googleServiceIntegration: GoogleServiceIntegration

    private val owner = "kartverket"
    private val repo = "test-repo"
    private val gcpToken = "gcp-token"
    private val gitHubToken = "github-token"

    @BeforeEach
    fun beforeEach() {
        githubConnector = mockk()
        googleServiceIntegration = mockk()
        validationService = ValidationService(githubConnector, googleServiceIntegration)
    }

    private fun repositoryInfoWithPermissions(vararg permissions: GitHubPermission) =
        RepositoryInfo(defaultBranch = "main", permissions = permissions.toList())

    private fun unauthorizedException(): WebClientResponseException =
        WebClientResponseException.create(
            HttpStatus.UNAUTHORIZED.value(),
            "Unauthorized",
            HttpHeaders.EMPTY,
            ByteArray(0),
            null,
        )

    @Nested
    inner class TestValidateAccessTokens {
        @Test
        fun `throws InvalidAccessTokensException when GitHub token is expired or invalid`() =
            runTest {
                coEvery {
                    githubConnector.fetchRepositoryInfo(gitHubToken, owner, repo)
                } throws unauthorizedException()

                assertThrows<InvalidAccessTokensException> {
                    validationService.validateAccessTokens(
                        gcpAccessToken = gcpToken,
                        gitHubAccessToken = gitHubToken,
                        gitHubPermissionNeeded = GitHubPermission.WRITE,
                        repositoryOwner = owner,
                        repositoryName = repo,
                    )
                }
            }

        @Test
        fun `propagates PermissionDeniedOnGitHubException when user has no read access`() =
            runTest {
                coEvery {
                    githubConnector.fetchRepositoryInfo(gitHubToken, owner, repo)
                } throws PermissionDeniedOnGitHubException("No pull access on $owner/$repo")

                assertThrows<PermissionDeniedOnGitHubException> {
                    validationService.validateAccessTokens(
                        gcpAccessToken = gcpToken,
                        gitHubAccessToken = gitHubToken,
                        gitHubPermissionNeeded = GitHubPermission.WRITE,
                        repositoryOwner = owner,
                        repositoryName = repo,
                    )
                }
            }

        @Test
        fun `throws AccessTokenValidationFailedException on unexpected GitHub errors`() =
            runTest {
                coEvery {
                    githubConnector.fetchRepositoryInfo(gitHubToken, owner, repo)
                } throws RuntimeException("Unexpected error")

                assertThrows<AccessTokenValidationFailedException> {
                    validationService.validateAccessTokens(
                        gcpAccessToken = gcpToken,
                        gitHubAccessToken = gitHubToken,
                        gitHubPermissionNeeded = GitHubPermission.WRITE,
                        repositoryOwner = owner,
                        repositoryName = repo,
                    )
                }
            }

        @Test
        fun `throws RepositoryAccessException when user has read-only access but write is required`() =
            runTest {
                coEvery {
                    githubConnector.fetchRepositoryInfo(gitHubToken, owner, repo)
                } returns repositoryInfoWithPermissions(GitHubPermission.READ)

                assertThrows<RepositoryAccessException> {
                    validationService.validateAccessTokens(
                        gcpAccessToken = gcpToken,
                        gitHubAccessToken = gitHubToken,
                        gitHubPermissionNeeded = GitHubPermission.WRITE,
                        repositoryOwner = owner,
                        repositoryName = repo,
                    )
                }
            }

        @Test
        fun `throws InvalidAccessTokensException when GCP token is invalid`() =
            runTest {
                coEvery {
                    githubConnector.fetchRepositoryInfo(gitHubToken, owner, repo)
                } returns repositoryInfoWithPermissions(GitHubPermission.READ, GitHubPermission.WRITE)

                coEvery { googleServiceIntegration.validateAccessToken(gcpToken) } returns false

                assertThrows<InvalidAccessTokensException> {
                    validationService.validateAccessTokens(
                        gcpAccessToken = gcpToken,
                        gitHubAccessToken = gitHubToken,
                        gitHubPermissionNeeded = GitHubPermission.WRITE,
                        repositoryOwner = owner,
                        repositoryName = repo,
                    )
                }
            }

        @Test
        fun `does not throw when tokens are valid and user has write access`() =
            runTest {
                coEvery {
                    githubConnector.fetchRepositoryInfo(gitHubToken, owner, repo)
                } returns repositoryInfoWithPermissions(GitHubPermission.READ, GitHubPermission.WRITE)

                coEvery { googleServiceIntegration.validateAccessToken(gcpToken) } returns true

                assertDoesNotThrow {
                    validationService.validateAccessTokens(
                        gcpAccessToken = gcpToken,
                        gitHubAccessToken = gitHubToken,
                        gitHubPermissionNeeded = GitHubPermission.WRITE,
                        repositoryOwner = owner,
                        repositoryName = repo,
                    )
                }
            }
    }
}
