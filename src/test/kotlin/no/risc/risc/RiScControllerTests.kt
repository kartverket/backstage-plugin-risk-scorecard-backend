package no.risc.risc

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import no.risc.config.AppConstants
import no.risc.getResource
import no.risc.github.GitHubAppService
import no.risc.github.GithubConnector
import no.risc.infra.connector.models.AccessTokens
import no.risc.infra.connector.models.GCPAccessToken
import no.risc.infra.connector.models.GithubAccessToken
import no.risc.risc.models.ContentStatus
import no.risc.risc.models.MigrationStatus
import no.risc.risc.models.MigrationVersions
import no.risc.risc.models.RiScContentResultDTO
import no.risc.risc.models.RiScStatus
import no.risc.slack.SlackService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RiScControllerTests {
    private lateinit var riScService: RiScService
    private lateinit var githubConnector: GithubConnector
    private lateinit var gitHubAppService: GitHubAppService
    private lateinit var slackService: SlackService
    private lateinit var controller: RiScController

    private val owner = "my-org"
    private val repository = "my-repo"
    private val riScId = "risc-456"
    private val gcpToken = "gcp-token-value"
    private val ghToken = "gh-token-value"

    private val stubbedGithubAccessToken = GithubAccessToken(ghToken)

    private val successDTO =
        RiScContentResultDTO(
            riScId = riScId,
            status = ContentStatus.Success,
            riScStatus = RiScStatus.Published,
            riScContent = getResource("github-response-data-1-v5.2.json"),
            migrationStatus =
                MigrationStatus(
                    migrationChanges = false,
                    migrationRequiresNewApproval = false,
                    migrationVersions = MigrationVersions(fromVersion = null, toVersion = null),
                ),
        )

    @BeforeEach
    fun setUp() {
        riScService = mockk()
        githubConnector = mockk()
        gitHubAppService = mockk()
        slackService = mockk()
        controller = RiScController(riScService, githubConnector, gitHubAppService, slackService)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    // ─────────────────────────────────────────────
    // getRiSc
    // ─────────────────────────────────────────────

    @Test
    fun `getRiSc delegates to fetchRiSc with LATEST_SUPPORTED_SCHEMA_VERSION`() =
        runTest {
            every { gitHubAppService.getGitHubAccessToken(ghToken) } returns stubbedGithubAccessToken
            coEvery {
                riScService.fetchRiSc(
                    owner = owner,
                    repository = repository,
                    accessTokens =
                        AccessTokens(
                            gcpAccessToken = GCPAccessToken(gcpToken),
                            githubAccessToken = stubbedGithubAccessToken,
                        ),
                    riScId = riScId,
                    latestSupportedVersion = AppConstants.LATEST_SUPPORTED_SCHEMA_VERSION,
                )
            } returns successDTO

            controller.getRiSc(
                gcpAccessToken = gcpToken,
                gitHubAccessToken = ghToken,
                repositoryOwner = owner,
                repositoryName = repository,
                id = riScId,
            )

            coVerify(exactly = 1) {
                riScService.fetchRiSc(
                    owner = owner,
                    repository = repository,
                    accessTokens =
                        AccessTokens(
                            gcpAccessToken = GCPAccessToken(gcpToken),
                            githubAccessToken = stubbedGithubAccessToken,
                        ),
                    riScId = riScId,
                    latestSupportedVersion = AppConstants.LATEST_SUPPORTED_SCHEMA_VERSION,
                )
            }
        }

    @Test
    fun `getRiSc returns the result from fetchRiSc unchanged`() =
        runTest {
            every { gitHubAppService.getGitHubAccessToken(ghToken) } returns stubbedGithubAccessToken
            coEvery { riScService.fetchRiSc(any(), any(), any(), any(), any()) } returns successDTO

            val result =
                controller.getRiSc(
                    gcpAccessToken = gcpToken,
                    gitHubAccessToken = ghToken,
                    repositoryOwner = owner,
                    repositoryName = repository,
                    id = riScId,
                )

            assertEquals(successDTO, result)
        }

    @Test
    fun `getRiSc with null GitHub token calls getGitHubAccessToken with null`() =
        runTest {
            val installationToken = GithubAccessToken("installation-token")
            every { gitHubAppService.getGitHubAccessToken(null) } returns installationToken
            coEvery { riScService.fetchRiSc(any(), any(), any(), any(), any()) } returns successDTO

            controller.getRiSc(
                gcpAccessToken = gcpToken,
                gitHubAccessToken = null,
                repositoryOwner = owner,
                repositoryName = repository,
                id = riScId,
            )

            verify(exactly = 1) { gitHubAppService.getGitHubAccessToken(null) }
        }

    // ─────────────────────────────────────────────
    // getRiScOfVersion
    // ─────────────────────────────────────────────

    @Test
    fun `getRiScOfVersion delegates to fetchRiSc with the provided latestSupportedVersion`() =
        runTest {
            val customVersion = "4.0"
            every { gitHubAppService.getGitHubAccessToken(ghToken) } returns stubbedGithubAccessToken
            coEvery { riScService.fetchRiSc(any(), any(), any(), any(), any()) } returns successDTO

            controller.getRiScOfVersion(
                gcpAccessToken = gcpToken,
                gitHubAccessToken = ghToken,
                repositoryOwner = owner,
                repositoryName = repository,
                latestSupportedVersion = customVersion,
                id = riScId,
            )

            coVerify(exactly = 1) {
                riScService.fetchRiSc(
                    owner = owner,
                    repository = repository,
                    accessTokens = any(),
                    riScId = riScId,
                    latestSupportedVersion = customVersion,
                )
            }
        }

    @Test
    fun `getRiScOfVersion passes repositoryOwner, repositoryName, and id correctly`() =
        runTest {
            val customVersion = "5.1"
            every { gitHubAppService.getGitHubAccessToken(ghToken) } returns stubbedGithubAccessToken
            coEvery { riScService.fetchRiSc(any(), any(), any(), any(), any()) } returns successDTO

            controller.getRiScOfVersion(
                gcpAccessToken = gcpToken,
                gitHubAccessToken = ghToken,
                repositoryOwner = owner,
                repositoryName = repository,
                latestSupportedVersion = customVersion,
                id = riScId,
            )

            coVerify(exactly = 1) {
                riScService.fetchRiSc(
                    owner = owner,
                    repository = repository,
                    accessTokens = any(),
                    riScId = riScId,
                    latestSupportedVersion = customVersion,
                )
            }
        }

    @Test
    fun `getRiScOfVersion with null GitHub token calls getGitHubAccessToken with null`() =
        runTest {
            val installationToken = GithubAccessToken("installation-token")
            every { gitHubAppService.getGitHubAccessToken(null) } returns installationToken
            coEvery { riScService.fetchRiSc(any(), any(), any(), any(), any()) } returns successDTO

            controller.getRiScOfVersion(
                gcpAccessToken = gcpToken,
                gitHubAccessToken = null,
                repositoryOwner = owner,
                repositoryName = repository,
                latestSupportedVersion = "5.0",
                id = riScId,
            )

            verify(exactly = 1) { gitHubAppService.getGitHubAccessToken(null) }
        }
}
