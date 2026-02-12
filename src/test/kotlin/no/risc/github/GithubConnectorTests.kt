package no.risc.github

import MockableResponse
import MockableWebClient
import deserializeContent
import io.mockk.every
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import mockableResponseFromObject
import no.risc.exception.exceptions.CreatePullRequestException
import no.risc.exception.exceptions.DeletingRiScException
import no.risc.exception.exceptions.PermissionDeniedOnGitHubException
import no.risc.github.models.GithubCommitInformation
import no.risc.github.models.GithubCommitObject
import no.risc.github.models.GithubCommitter
import no.risc.github.models.GithubCreateNewBranchPayload
import no.risc.github.models.GithubCreateNewPullRequestPayload
import no.risc.github.models.GithubDeleteFilePayload
import no.risc.github.models.GithubFileDTO
import no.risc.github.models.GithubPullRequestBranch
import no.risc.github.models.GithubPullRequestObject
import no.risc.github.models.GithubReferenceObjectDTO
import no.risc.github.models.GithubRepositoryDTO
import no.risc.github.models.GithubRepositoryPermissions
import no.risc.github.models.GithubStatus
import no.risc.github.models.GithubWriteToFilePayload
import no.risc.infra.connector.models.GitHubPermission
import no.risc.infra.connector.models.GithubAccessToken
import no.risc.risc.models.LastPublished
import no.risc.risc.models.ProcessingStatus
import no.risc.risc.models.RiScIdentifier
import no.risc.risc.models.RiScStatus
import no.risc.risc.models.UserInfo
import no.risc.utils.encodeBase64
import no.risc.utils.generateRandomAlphanumericString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBody
import java.time.OffsetDateTime

class GithubConnectorTests {
    private lateinit var githubConnector: GithubConnector
    private lateinit var webClient: MockableWebClient
    private val filenamePostfix = "risc"
    private val filenamePrefix = "risc"
    private val riscFolderPath = ".security/risc"
    private val owner = "owner"
    private val repository = "risc-repo"
    private val initRiScRepoName = "init-risc-repo"
    private val initRiScRepoOwner = "owner"

    @BeforeEach
    fun beforeEach() {
        webClient = MockableWebClient()
        githubConnector =
            spyk(
                GithubConnector(
                    filenamePrefix = filenamePrefix,
                    filenamePostfix = filenamePostfix,
                    githubHelper =
                        GithubHelper(
                            filenamePrefix = filenamePrefix,
                            filenamePostfix = filenamePostfix,
                            riScFolderPath = riscFolderPath,
                            initRiScRepoName = initRiScRepoName,
                            initRiScRepoOwner = initRiScRepoOwner,
                        ),
                ),
            )
        every { githubConnector.webClient } returns webClient.webClient
    }

    private fun randomSHA(): String = generateRandomAlphanumericString(41)

    private fun randomRiSc(): String = riScName(generateRandomAlphanumericString(5))

    private fun riScName(riScId: String) = "$filenamePrefix-$riScId"

    private fun riScFilename(riScId: String) = "$riScId.$filenamePostfix.yaml"

    private fun pathToRiSC(riScId: String) = "$riscFolderPath/${riScFilename(riScId)}"

    @Nested
    inner class TestFetchRiScGithubMetadata {
        private val pathToDraftIdentifiers = "/$owner/$repository/git/matching-refs/heads/$filenamePrefix-"
        private val pathToRiScFiles = "/$owner/$repository/contents/$riscFolderPath"
        private val pathToOpenPullRequests = "/$owner/$repository/pulls"

        private fun queueRiScResponses(
            riscIdsFromMainFiles: List<String>,
            riscIdsFromBranches: List<String>,
            riscIdsWithPR: List<String>,
        ) {
            webClient.queueResponse(
                response =
                    mockableResponseFromObject(
                        riscIdsFromMainFiles.map { riScID ->
                            GithubFileDTO(
                                content = "{}",
                                sha = randomSHA(),
                                name = "$riScID.$filenamePostfix.yaml",
                            )
                        },
                    ),
                path = pathToRiScFiles,
            )

            webClient.queueResponse(
                response =
                    mockableResponseFromObject(
                        riscIdsFromBranches.map { riScID ->
                            GithubReferenceObjectDTO(
                                ref = "refs/heads/$riScID",
                                url = "https://api.github.com/repos/$owner/$repository/git/refs/heads/$riScID",
                            )
                        },
                    ),
                path = pathToDraftIdentifiers,
            )

            webClient.queueResponse(
                response =
                    mockableResponseFromObject(
                        riscIdsWithPR.mapIndexed { index, riScID ->
                            GithubPullRequestObject(
                                url = "https://api.github.com/repos/$owner/$repository/pulls/$index",
                                title = "Update RiSc",
                                createdAt = OffsetDateTime.now(),
                                head = GithubPullRequestBranch(riScID),
                                base = GithubPullRequestBranch("main"),
                                number = index,
                            )
                        },
                    ),
                path = pathToOpenPullRequests,
            )
        }

        private fun fetchRiScGithubMetadata(): List<RiScGithubMetadata> =
            runBlocking {
                githubConnector.fetchRiScGithubMetadata(
                    owner = owner,
                    repository = repository,
                    githubAccessToken = GithubAccessToken("accessToken"),
                )
            }

        @Test
        fun `test fetch github metadata for all riScs in repository`() {
            val riScs =
                listOf(
                    riScName("aaa0a"),
                    riScName("bbb1b"),
                    riScName("ccc2c"),
                    riScName("ddd3d"),
                    riScName("eee4e"),
                )
            val riscIdsFromMainFiles = listOf(riScs[0], riScs[1], riScs[2])
            val riscIdsFromBranches = listOf(riScs[1], riScs[2], riScs[3], riScs[4])
            val riscIdsWithPR = listOf(riScs[2], riScs[3])

            queueRiScResponses(riscIdsFromMainFiles, riscIdsFromBranches, riscIdsWithPR)
            val githubMetadata = fetchRiScGithubMetadata()

            assertEquals(
                5,
                githubMetadata.size,
                "All unique risc identifiers should be found",
            )

            assertTrue({
                riscIdsFromMainFiles.all { riScID ->
                    githubMetadata.any { it.id == riScID && it.isStoredInMain }
                }
            }, "Metadata of unique published RiScs should be included in the list")
            assertTrue({
                riscIdsFromBranches.all { riScID ->
                    githubMetadata.any { it.id == riScID && it.hasBranch }
                }
            }, "Metadata of unique drafted RiScs should be included in the list")
            assertTrue({
                riscIdsWithPR.all { riScID ->
                    githubMetadata.any { it.id == riScID && it.hasOpenPR && it.prUrl != null }
                }
            }, "Metadata of unique approved RiScs should be included in the list")
        }

        @Test
        fun `test fetch github metadata for all riScs in repository has correct properties`() {
            val riScs =
                listOf(
                    riScName("aaa0a"),
                    riScName("bbb1b"),
                    riScName("ccc2c"),
                    riScName("ddd3d"),
                    riScName("eee4e"),
                )
            val riscIdsFromMainFiles = listOf(riScs[0], riScs[1], riScs[2])
            val riscIdsFromBranches = listOf(riScs[1], riScs[2], riScs[3], riScs[4])
            val riscIdsWithPR = listOf(riScs[2], riScs[3])

            queueRiScResponses(riscIdsFromMainFiles, riscIdsFromBranches, riscIdsWithPR)
            val githubMetadata = fetchRiScGithubMetadata()

            for (m in githubMetadata) {
                when (m.id) {
                    riScName("aaa0a") -> {
                        assertTrue(
                            m.isStoredInMain && !m.hasBranch && !m.hasOpenPR && m.prUrl == null,
                            "This riSc should only exist in main",
                        )
                    }

                    riScName("bbb1b") -> {
                        assertTrue(
                            m.isStoredInMain && m.hasBranch && !m.hasOpenPR && m.prUrl == null,
                            "This riSc should exist in main and have a branch",
                        )
                    }

                    riScName("ccc2c") -> {
                        assertTrue(
                            m.isStoredInMain && m.hasBranch && m.hasOpenPR && m.prUrl != null,
                            "This riSc should exist in main, have a branch, and an open PR",
                        )
                    }

                    riScName("ddd3d") -> {
                        assertTrue(
                            !m.isStoredInMain && m.hasBranch && m.hasOpenPR && m.prUrl != null,
                            "This riSc should not exist in main, but have a branch and an open PR",
                        )
                    }

                    riScName("eee4e") -> {
                        assertTrue(
                            !m.isStoredInMain && m.hasBranch && !m.hasOpenPR && m.prUrl == null,
                            "This riSc should only exist in a branch",
                        )
                    }
                }
            }
        }

        @Test
        fun `test fetch github metadata for all riScs in repository with retrieval errors`() {
            webClient.queueResponse(
                response = MockableResponse(content = null, httpStatus = HttpStatus.INTERNAL_SERVER_ERROR),
                path = pathToDraftIdentifiers,
            )

            webClient.queueResponse(
                response = MockableResponse(content = null, httpStatus = HttpStatus.BAD_REQUEST),
                path = pathToRiScFiles,
            )

            webClient.queueResponse(
                response = MockableResponse(content = null, httpStatus = HttpStatus.FORBIDDEN),
                path = pathToOpenPullRequests,
            )

            val message = "Fetch all RiSc identifiers should actually throw so we don't return 200 with and empty list"
            assertThrows<WebClientResponseException>(message) { fetchRiScGithubMetadata() }
        }
    }

    @Nested
    inner class TestFetchRiscContent {
        private fun pathToRiScContent(riScId: String) = "/$owner/$repository/contents/$riscFolderPath/${riScFilename(riScId)}"

        private fun pathToRiScContentOnDraftBranch(riScId: String) =
            "/$owner/$repository/contents/$riscFolderPath/${riScFilename(riScId)}?ref=$riScId"

        private fun fetchPublishedRiSc(riScId: String) =
            runBlocking {
                githubConnector.fetchPublishedRiSc(
                    owner = owner,
                    repository = repository,
                    id = riScId,
                    accessToken = "accessToken",
                )
            }

        @Test
        fun `test fetch RiSc content`() {
            val riScId = randomRiSc()
            val content = """{ "schemaVersion": "4.0" }"""

            webClient.queueResponse(
                response =
                    mockableResponseFromObject(
                        GithubFileDTO(
                            content = content.encodeBase64(),
                            sha = randomSHA(),
                            name = riScFilename(riScId),
                        ),
                    ),
                path = pathToRiScContent(riScId),
            )

            val response = fetchPublishedRiSc(riScId)

            assertEquals(
                GithubStatus.Success,
                response.status,
                "When the RiSc exists the status should be set to Success.",
            )
            assertEquals(content, response.data, "When the RiSc exists the data should be set to the content.")
        }

        @Test
        fun `test fetch RiSc content does not exist`() {
            val riScId = randomRiSc()

            webClient.queueResponse(
                response = MockableResponse(content = null, httpStatus = HttpStatus.NOT_FOUND),
                path = pathToRiScContent(riScId),
            )

            val response = fetchPublishedRiSc(riScId)

            assertEquals(
                GithubStatus.NotFound,
                response.status,
                "When the RiSc does not exist, the status should be set to NotFound.",
            )
            assertEquals(null, response.data, "When the RiSc does not exist, there should be no data.")
        }

        @Test
        fun `test fetch RiSc content no access`() {
            val riScId = randomRiSc()

            webClient.queueResponse(
                response = MockableResponse(content = null, httpStatus = HttpStatus.UNAUTHORIZED),
                path = pathToRiScContent(riScId),
            )

            val response = fetchPublishedRiSc(riScId)

            assertEquals(
                GithubStatus.Unauthorized,
                response.status,
                "When the user does not have access, the status should be set to Unauthorized.",
            )
            assertEquals(null, response.data, "When the user does not have access, there should be no data.")
        }

        private fun fetchDraftedRiSc(riScId: String) =
            runBlocking {
                githubConnector.fetchDraftedRiScContent(
                    owner = owner,
                    repository = repository,
                    id = RiScIdentifier(riScId, status = RiScStatus.Draft),
                    accessToken = "accessToken",
                )
            }

        @Test
        fun `test fetch draft RiSc content`() {
            val riScId = randomRiSc()
            val content = """{ "schemaVersion": "4.0" }"""

            webClient.queueResponse(
                response =
                    mockableResponseFromObject(
                        GithubFileDTO(
                            content = content.encodeBase64(),
                            sha = randomSHA(),
                            name = riScFilename(riScId),
                        ),
                    ),
                path = pathToRiScContentOnDraftBranch(riScId),
            )

            val response = fetchDraftedRiSc(riScId)

            assertEquals(
                GithubStatus.Success,
                response.status,
                "When the RiSc exists the status should be set to Success.",
            )
            assertEquals(content, response.data, "When the RiSc exists the data should be set to the content.")
        }

        @Test
        fun `test fetch draft RiSc content does not exist`() {
            val riScId = randomRiSc()

            webClient.queueResponse(
                response = MockableResponse(content = null, httpStatus = HttpStatus.NOT_FOUND),
                path = pathToRiScContentOnDraftBranch(riScId),
            )

            val response = fetchDraftedRiSc(riScId)

            assertEquals(
                GithubStatus.NotFound,
                response.status,
                "When the RiSc does not exist, the status should be set to NotFound.",
            )
            assertEquals(null, response.data, "When the RiSc does not exist, there should be no data.")
        }

        @Test
        fun `test fetch draft RiSc content no access`() {
            val riScId = randomRiSc()

            webClient.queueResponse(
                response = MockableResponse(content = null, httpStatus = HttpStatus.UNAUTHORIZED),
                path = pathToRiScContentOnDraftBranch(riScId),
            )

            val response = fetchDraftedRiSc(riScId)

            assertEquals(
                GithubStatus.Unauthorized,
                response.status,
                "When the user does not have access, the status should be set to Unauthorized.",
            )
            assertEquals(null, response.data, "When the user does not have access, there should be no data.")
        }
    }

    @Nested
    inner class TestFetchRepositoryInfo {
        private val pathToRepository = "/$owner/$repository"

        private fun fetchRepositoryInfo() =
            runBlocking {
                githubConnector.fetchRepositoryInfo(
                    gitHubAccessToken = "accessToken",
                    repositoryOwner = owner,
                    repositoryName = repository,
                )
            }

        @Test
        fun `test fetch repository info with push access`() {
            val defaultBranch = "default"
            webClient.queueResponse(
                response =
                    mockableResponseFromObject(
                        GithubRepositoryDTO(
                            defaultBranch = defaultBranch,
                            permissions =
                                GithubRepositoryPermissions(
                                    push = true,
                                    admin = false,
                                    maintain = false,
                                    triage = false,
                                    pull = true,
                                ),
                        ),
                    ),
                path = pathToRepository,
            )

            val response = fetchRepositoryInfo()

            assertEquals(
                defaultBranch,
                response.defaultBranch,
                "Default branch should be the one returned from GitHub.",
            )
            assertEquals(
                GitHubPermission.entries.toList(),
                response.permissions,
                "If the user has push access to the repository, both push and pull access should be returned.",
            )
        }

        @Test
        fun `test fetch repository info with only pull access`() {
            val defaultBranch = "default"
            webClient.queueResponse(
                response =
                    mockableResponseFromObject(
                        GithubRepositoryDTO(
                            defaultBranch = defaultBranch,
                            permissions =
                                GithubRepositoryPermissions(
                                    push = false,
                                    admin = false,
                                    maintain = false,
                                    triage = false,
                                    pull = true,
                                ),
                        ),
                    ),
                path = pathToRepository,
            )

            val response = fetchRepositoryInfo()

            assertEquals(
                defaultBranch,
                response.defaultBranch,
                "Default branch should be the one returned from GitHub.",
            )
            assertEquals(
                listOf(GitHubPermission.READ),
                response.permissions,
                "If the user only has pull access to the repository, only pull access should be returned.",
            )
        }

        @Test
        fun `test fetch repository info with no pull access`() {
            val defaultBranch = "default"
            webClient.queueResponse(
                response =
                    mockableResponseFromObject(
                        GithubRepositoryDTO(
                            defaultBranch = defaultBranch,
                            permissions =
                                GithubRepositoryPermissions(
                                    push = false,
                                    admin = false,
                                    maintain = false,
                                    triage = false,
                                    pull = false,
                                ),
                        ),
                    ),
                path = pathToRepository,
            )

            assertThrows<PermissionDeniedOnGitHubException>("If the user does not have pull access, an error should be thrown") {
                fetchRepositoryInfo()
            }
        }
    }

    @Nested
    inner class TestPullRequests {
        private val pathToPullRequestEndpoint = "/$owner/$repository/pulls"
        private val pathToRepositoryInfoEndpoint = "/$owner/$repository"

        private fun pathToDraftRiScContent(riScId: String) =
            "/$owner/$repository/contents/$riscFolderPath/${riScFilename(riScId)}?ref=$riScId"

        @Test
        fun `test fetch all pull requests`() {
            val pullRequests =
                listOf(
                    GithubPullRequestObject(
                        url = "https://api.github.com/repos/$owner/$repository/pulls/97",
                        title = "Unicode Support",
                        createdAt = OffsetDateTime.now().minusHours(2),
                        head = GithubPullRequestBranch(ref = "feature/unicode"),
                        base = GithubPullRequestBranch(ref = "main"),
                        number = 97,
                    ),
                    GithubPullRequestObject(
                        url = "https://api.github.com/repos/$owner/$repository/pulls/84",
                        title = " Updated risk scorecard",
                        createdAt = OffsetDateTime.now().minusHours(3).minusMinutes(18),
                        head = GithubPullRequestBranch(ref = randomRiSc()),
                        base = GithubPullRequestBranch(ref = "main"),
                        number = 84,
                    ),
                )

            webClient.queueResponse(
                response = mockableResponseFromObject(pullRequests),
                path = pathToPullRequestEndpoint,
            )

            val result = runBlocking { githubConnector.fetchAllPullRequests(owner, repository, "access token") }

            assertEquals(2, result.size, "All PRs returned from GitHub should be included and no more.")
            assertTrue(
                pullRequests.all { pullRequest -> result.any { it == pullRequest } },
                "All returned PRs should be included with the correct values.",
            )
        }

        @Test
        fun `test fetch all pull requests empty list on errors`() {
            webClient.queueResponse(
                response = MockableResponse(content = null, httpStatus = HttpStatus.INTERNAL_SERVER_ERROR),
            )

            val result = runBlocking { githubConnector.fetchAllPullRequests(owner, repository, "access token") }

            assertEquals(0, result.size, "On an error, an empty list should be returned.")
        }

        private fun queueDefaultBranchResponse(defaultBranch: String) {
            webClient.queueResponse(
                response =
                    mockableResponseFromObject(
                        GithubRepositoryDTO(
                            defaultBranch = defaultBranch,
                            permissions =
                                GithubRepositoryPermissions(
                                    admin = false,
                                    maintain = false,
                                    push = true,
                                    triage = false,
                                    pull = true,
                                ),
                        ),
                    ),
                path = pathToRepositoryInfoEndpoint,
            )
        }

        @Test
        fun `test create pull request for RiSc update`() {
            val riScId = randomRiSc()
            val baseBranch = "main"

            val pullRequest =
                GithubPullRequestObject(
                    url = "https://api.github.com/repos/$owner/$repository/pulls/29",
                    title = "Updated risk scorecard",
                    createdAt = OffsetDateTime.now(),
                    head = GithubPullRequestBranch(riScId),
                    base = GithubPullRequestBranch(baseBranch),
                    number = 37,
                )

            queueDefaultBranchResponse(baseBranch)

            webClient.queueResponse(
                response = mockableResponseFromObject(pullRequest),
                path = pathToPullRequestEndpoint,
            )

            webClient.queueResponse(
                response = mockableResponseFromObject(GithubFileDTO(content = "{}", sha = randomSHA(), name = riScId)),
                path = pathToDraftRiScContent(riScId),
            )

            val result =
                runBlocking {
                    githubConnector.createPullRequestForRiSc(
                        owner = owner,
                        repository = repository,
                        riScId = riScId,
                        requiresNewApproval = true,
                        gitHubAccessToken = "access token",
                        userInfo = UserInfo(name = "Kari Nordmann", email = "kari.nordmann@test.com"),
                    )
                }

            assertEquals(pullRequest, result, "The created pull request should be returned.")

            val request = webClient.getNextRequest(pathToPullRequestEndpoint)

            val requestContent = request.deserializeContent<GithubCreateNewPullRequestPayload>()

            assertEquals(baseBranch, requestContent.base, "PR should be to the provided base branch.")
            assertEquals("$owner:$riScId", requestContent.head, "PR should be from the RiSc branch.")
            assertEquals(
                "Updated risk scorecard",
                requestContent.title,
                "PR should be for an update when the RiSc file exists.",
            )
        }

        @Test
        fun `test create pull request for RiSc deletion`() {
            val riScId = randomRiSc()
            val baseBranch = "base"

            val pullRequest =
                GithubPullRequestObject(
                    url = "https://api.github.com/repos/$owner/$repository/pulls/29",
                    title = "Deleted risk scorecard",
                    createdAt = OffsetDateTime.now(),
                    head = GithubPullRequestBranch(riScId),
                    base = GithubPullRequestBranch(baseBranch),
                    number = 37,
                )

            queueDefaultBranchResponse(baseBranch)

            webClient.queueResponse(
                response = mockableResponseFromObject(pullRequest),
                path = pathToPullRequestEndpoint,
            )

            // Deletion
            webClient.queueResponse(
                response = MockableResponse(content = null, httpStatus = HttpStatus.NOT_FOUND),
                path = pathToDraftRiScContent(riScId),
            )

            val result =
                runBlocking {
                    githubConnector.createPullRequestForRiSc(
                        owner = owner,
                        repository = repository,
                        riScId = riScId,
                        requiresNewApproval = true,
                        gitHubAccessToken = "access token",
                        userInfo = UserInfo(name = "Kari Nordmann", email = "kari.nordmann@test.com"),
                    )
                }

            assertEquals(pullRequest, result, "The created pull request should be returned.")

            val request = webClient.getNextRequest(pathToPullRequestEndpoint)

            val requestContent = request.deserializeContent<GithubCreateNewPullRequestPayload>()

            assertEquals(baseBranch, requestContent.base, "PR should be to the provided base branch.")
            assertEquals("$owner:$riScId", requestContent.head, "PR should be from the RiSc branch.")
            assertEquals(
                "Deleted risk scorecard",
                requestContent.title,
                "PR should be for a deletion when the RiSc file does not exist.",
            )
        }

        @Test
        fun `test create pull request for RiSc throws exception on error`() {
            val riScId = randomRiSc()
            webClient.queueResponse(
                response = MockableResponse(content = null, httpStatus = HttpStatus.BAD_REQUEST),
                path = pathToPullRequestEndpoint,
            )

            webClient.queueResponse(
                response = mockableResponseFromObject(GithubFileDTO(content = "{}", sha = randomSHA(), name = riScId)),
                path = pathToDraftRiScContent(riScId),
            )

            queueDefaultBranchResponse("default")

            assertThrows<CreatePullRequestException> {
                runBlocking {
                    githubConnector.createPullRequestForRiSc(
                        owner = owner,
                        repository = repository,
                        riScId = riScId,
                        requiresNewApproval = false,
                        gitHubAccessToken = "access token",
                        userInfo = UserInfo(name = "Ola Nordmann", email = "ola.nordmann@test.com"),
                    )
                }
            }
        }
    }

    @Nested
    inner class TestFetchLastPublishedRiScDateAndCommitNumber {
        private fun pathToRiScCommits(riScId: String) = "/$owner/$repository/commits?path=${pathToRiSC(riScId)}"

        private fun pathToDefaultBranchCommitsSince(since: OffsetDateTime) = "/$owner/$repository/commits?since=$since"

        private fun githubCommitObjectWithRandomSha(date: OffsetDateTime): GithubCommitObject {
            val sha = randomSHA()
            return GithubCommitObject(
                sha = sha,
                url = "https://api.github.com/repos/$owner/$repository/commits/",
                commit =
                    GithubCommitInformation(
                        message = "Commit message",
                        committer =
                            GithubCommitter(
                                date = date,
                                name = "Committer",
                            ),
                    ),
            )
        }

        private fun fetchLastPublished(riScId: String): LastPublished? =
            runBlocking {
                githubConnector.fetchLastPublishedRiScDateAndCommitNumber(
                    owner = owner,
                    repository = repository,
                    accessToken = "accessToken",
                    riScId = riScId,
                )
            }

        @Test
        fun `test fetch last published RiSc date and commit number`() {
            val riScId = randomRiSc()
            val lastCommitTime = OffsetDateTime.now().minusYears(1)

            webClient.queueResponse(
                response =
                    mockableResponseFromObject(
                        listOf(
                            githubCommitObjectWithRandomSha(lastCommitTime),
                            githubCommitObjectWithRandomSha(lastCommitTime.minusDays(18).minusHours(12)),
                            githubCommitObjectWithRandomSha(lastCommitTime.minusMonths(2).minusDays(13)),
                        ),
                    ),
                path = "/$owner/$repository/commits?path=${pathToRiSC(riScId)}&per_page=1",
            )

            webClient.queueResponse(
                response =
                    mockableResponseFromObject(
                        listOf(
                            githubCommitObjectWithRandomSha(lastCommitTime.plusMonths(3).plusDays(1)),
                            githubCommitObjectWithRandomSha(lastCommitTime.plusMonths(2).plusDays(7)),
                            githubCommitObjectWithRandomSha(lastCommitTime.plusDays(1).plusHours(3)),
                            githubCommitObjectWithRandomSha(lastCommitTime.plusDays(1).plusHours(2)),
                            githubCommitObjectWithRandomSha(lastCommitTime.plusMinutes(8)),
                            githubCommitObjectWithRandomSha(lastCommitTime),
                            githubCommitObjectWithRandomSha(lastCommitTime.minusDays(5).minusHours(12)),
                            githubCommitObjectWithRandomSha(lastCommitTime.minusDays(18).minusHours(12)),
                            githubCommitObjectWithRandomSha(lastCommitTime.minusMonths(1).minusDays(24)),
                            githubCommitObjectWithRandomSha(lastCommitTime.minusMonths(2).minusDays(13)),
                        ),
                    ),
                path = "/$owner/$repository/commits?since=$lastCommitTime&per_page=100&page=1",
            )
            webClient.queueResponse(
                response = mockableResponseFromObject(emptyList<GithubCommitObject>()),
                path = "/$owner/$repository/commits?since=$lastCommitTime&per_page=100&page=2",
            )

            val lastPublished = fetchLastPublished(riScId)

            assertNotNull(
                lastPublished,
                "If both GitHub API calls answer correctly, last published should not be null.",
            )
            assertEquals(
                lastCommitTime,
                lastPublished.dateTime,
                "The returned time should be equal to the time of the last published commit to the RiSc.",
            )
            assertEquals(
                5,
                lastPublished.numberOfCommits,
                "The number of commits since last publication of the RiSc should count exactly the number of commits happening since the last commit to the RiSc.",
            )
        }

        @Test
        fun `test fetch last published RiSc date and commit number returns null if no commit exists for RiSc`() {
            val riScId = randomRiSc()
            val lastCommitTime = OffsetDateTime.now()

            webClient.queueResponse(
                response = mockableResponseFromObject(emptyList<GithubCommitObject>()),
                path = pathToRiScCommits(riScId),
            )

            webClient.queueResponse(
                response =
                    mockableResponseFromObject(
                        listOf(
                            githubCommitObjectWithRandomSha(lastCommitTime),
                            githubCommitObjectWithRandomSha(lastCommitTime.minusDays(5).minusHours(12)),
                            githubCommitObjectWithRandomSha(lastCommitTime.minusDays(13).minusMinutes(8)),
                            githubCommitObjectWithRandomSha(lastCommitTime.minusMonths(1).minusDays(13)),
                        ),
                    ),
                path = pathToDefaultBranchCommitsSince(lastCommitTime),
            )

            val lastPublished = fetchLastPublished(riScId)

            assertNull(lastPublished, "If the RiSc file does not have any commits, null should be returned.")
        }

        @Test
        fun `test fetch last published RiSc date and commit number returns null on error`() {
            val riScId = randomRiSc()
            val lastCommitTime = OffsetDateTime.now()

            webClient.queueResponse(
                response = mockableResponseFromObject(listOf(githubCommitObjectWithRandomSha(lastCommitTime))),
                path = pathToRiScCommits(riScId),
            )

            webClient.queueResponse(
                response = MockableResponse(content = null, httpStatus = HttpStatus.BAD_REQUEST),
                path = pathToDefaultBranchCommitsSince(lastCommitTime),
            )

            val lastPublished = fetchLastPublished(riScId)

            assertNull(lastPublished, "If calls to the GitHub API fail, null should be returned.")
        }
    }

    @Nested
    inner class TestPutFileRequest {
        private fun fileURL(
            path: String,
            branch: String,
        ) = "/$owner/$repository/contents/$path?ref=$branch"

        /* A response string mimicking parts of the response from the GitHub API. The response is not processed in the
           application beyond a string. There is thus no datatype to use for the answer. */
        private fun constructResponseString(path: String) =
            """
            {
                "content": {
                    "name": "${path.split("/").last()}",
                    "path": "$path",
                    "sha": "${randomSHA()}",
                },
                "commit": {
                    "sha": "${randomSHA()}",
                }
            }
            """.trimIndent()

        private fun putFileRequest(
            filePath: String,
            commitMessage: String,
            content: String,
            branch: String,
        ): String =
            runBlocking {
                githubConnector
                    .putFileRequestToGithub(
                        repositoryOwner = owner,
                        repositoryName = repository,
                        gitHubAccessToken = GithubAccessToken("accessToken"),
                        filePath = filePath,
                        message = commitMessage,
                        content = content,
                        branch = branch,
                    ).awaitBody<String>()
            }

        @Test
        fun `test file creation with put file request`() {
            val filePath = pathToRiSC(randomRiSc())
            val branch = "main"

            // The file does not exist, so there is no file contents available.
            webClient.queueResponse(
                response = MockableResponse(content = null, httpStatus = HttpStatus.NOT_FOUND),
                path = fileURL(filePath, branch),
                method = HttpMethod.GET,
            )

            val responseString = constructResponseString(filePath)
            webClient.queueResponse(
                response = MockableResponse(content = responseString),
                path = fileURL(filePath, branch),
                method = HttpMethod.PUT,
            )

            val response =
                putFileRequest(
                    filePath = filePath,
                    commitMessage = "Creating new RiSc",
                    content = """{ "schemaVersion": "4.0" }""",
                    branch = branch,
                )

            assertEquals(
                responseString,
                response,
                "The file content provided should be equal to the one returned by the GitHub API.",
            )

            // Ignore file info request
            webClient.getNextRequest()

            val requestBody = webClient.getNextRequest().deserializeContent<GithubWriteToFilePayload>()
            assertNull(requestBody.sha, "The SHA should be null if the file does not already exist.")
        }

        @Test
        fun `test file update with put file request`() {
            val filePath = pathToRiSC(randomRiSc())
            val branch = "default"

            val fileSHA = randomSHA()

            // The file already exists, so any GET request for the file should return information about it.
            webClient.queueResponse(
                response =
                    mockableResponseFromObject(
                        GithubFileDTO(
                            content = """{ "schemaVersion": "4.0" }""",
                            sha = fileSHA,
                            name = filePath.split("/").last(),
                        ),
                    ),
                path = fileURL(filePath, branch),
                method = HttpMethod.GET,
            )

            val responseString = constructResponseString(filePath)
            webClient.queueResponse(
                response = MockableResponse(content = responseString),
                path = fileURL(filePath, branch),
                method = HttpMethod.PUT,
            )

            val response =
                putFileRequest(
                    filePath = filePath,
                    commitMessage = "Updating RiSc",
                    content = """{ "schemaVersion": "4.1" }""",
                    branch = branch,
                )

            assertEquals(
                responseString,
                response,
                "The file content provided should be equal to the one returned by the GitHub API.",
            )

            // Ignore file info request
            webClient.getNextRequest()

            val requestBody = webClient.getNextRequest().deserializeContent<GithubWriteToFilePayload>()
            assertEquals(fileSHA, requestBody.sha, "The SHA should be equal to the file SHA if the file already exist.")
        }

        @Test
        fun `test put file request throws exception on error`() {
            val filePath = pathToRiSC(randomRiSc())
            val branch = "dev"

            val fileSHA = randomSHA()

            // The file already exists, so any GET request for the file should return information about it.
            webClient.queueResponse(
                response =
                    mockableResponseFromObject(
                        GithubFileDTO(
                            content = """{ "schemaVersion": "4.0" }""",
                            sha = fileSHA,
                            name = filePath.split("/").last(),
                        ),
                    ),
                path = fileURL(filePath, branch),
                method = HttpMethod.GET,
            )

            // Want to check error handling on API errors.
            webClient.queueResponse(
                response = MockableResponse(content = null, httpStatus = HttpStatus.INTERNAL_SERVER_ERROR),
                path = fileURL(filePath, branch),
                method = HttpMethod.PUT,
            )

            assertThrows<Exception> {
                putFileRequest(
                    filePath = filePath,
                    commitMessage = "Updating RiSc",
                    content = """{ "schemaVersion": "4.1" }""",
                    branch = branch,
                )
            }
        }
    }

    @Nested
    inner class TestCreateNewBranch {
        private fun urlToCommitsOnBranch(branch: String) = "/$owner/$repository/commits/heads/$branch"

        private fun githubCommitObject(sha: String) =
            GithubCommitObject(
                sha = sha,
                url = "https://api.github.com/repos/$owner/$repository/commits/",
                commit =
                    GithubCommitInformation(
                        message = "Commit message",
                        committer =
                            GithubCommitter(
                                date = OffsetDateTime.now().minusHours(4),
                                name = "Committer",
                            ),
                    ),
            )

        private val urlToCreateBranch = "/$owner/$repository/git/refs"

        /* A response string mimicking parts of the response from the GitHub API. The response is not processed in the
            application beyond a string. There is thus no datatype to use for the answer. */
        private fun constructResponseString(
            sha: String,
            newBranch: String,
        ) = """
            {
              "ref": "refs/heads/$newBranch",
              "url": "https://api.github.com/repos/$owner/$repository/git/refs/heads/$newBranch",
              "object": {
                "type": "commit",
                "sha": "$sha",
                "url": "https://api.github.com/repos/$owner/$repository/git/commits/$sha"
              }
            }
            """.trimIndent()

        private fun createNewBranch(
            baseBranch: String,
            newBranch: String,
        ) = runBlocking {
            githubConnector.createNewBranch(
                owner = owner,
                repository = repository,
                newBranchName = newBranch,
                accessToken = "accessToken",
                baseBranch = baseBranch,
            )
        }

        @Test
        fun `test create new branch`() {
            val branch = "main"
            val newBranch = randomRiSc()
            val sha = randomSHA()

            webClient.queueResponse(
                response = mockableResponseFromObject(githubCommitObject(sha)),
                path = urlToCommitsOnBranch(branch),
            )

            val expectedResponse = constructResponseString(sha = sha, newBranch = newBranch)
            webClient.queueResponse(
                response = MockableResponse(content = expectedResponse),
                path = urlToCreateBranch,
            )

            val response = createNewBranch(baseBranch = branch, newBranch = newBranch)

            assertEquals(
                expectedResponse,
                response,
                "If a new branch is created, the method should return the same JSON as returned by the GitHub API.",
            )

            // Ignore request to get SHA
            webClient.getNextRequest()

            val requestToCreate = webClient.getNextRequest().deserializeContent<GithubCreateNewBranchPayload>()

            assertEquals(
                sha,
                requestToCreate.shaToBranchFrom,
                "The SHA to branch from should be equivalent to the SHA of the last commit on the base branch.",
            )
        }

        @Test
        fun `test create new branch errors when branch does not exist`() {
            val branch = "default"
            val newBranch = randomRiSc()
            val sha = randomSHA()

            webClient.queueResponse(
                response = MockableResponse(content = null, httpStatus = HttpStatus.NOT_FOUND),
                path = urlToCommitsOnBranch(branch),
            )

            webClient.queueResponse(
                response = MockableResponse(content = constructResponseString(sha = sha, newBranch = newBranch)),
                path = urlToCreateBranch,
            )

            assertThrows<Exception>("Creation of a new branch should error when the base branch does not exist.") {
                createNewBranch(baseBranch = branch, newBranch = newBranch)
            }

            assertTrue(
                webClient.hasQueuedUpResponses(urlToCreateBranch),
                "If the base branch does not exist, the method should not attempt to construct a new branch.",
            )
        }

        @Test
        fun `test create new branch throws exception on error`() {
            val branch = "standard"
            val newBranch = randomRiSc()
            val sha = randomSHA()

            webClient.queueResponse(
                response = MockableResponse(content = null, httpStatus = HttpStatus.NOT_FOUND),
                path = urlToCommitsOnBranch(branch),
            )

            webClient.queueResponse(
                response = mockableResponseFromObject(githubCommitObject(sha)),
                path = urlToCommitsOnBranch(branch),
            )

            webClient.queueResponse(
                response = MockableResponse(content = null, httpStatus = HttpStatus.BAD_REQUEST),
                path = urlToCreateBranch,
            )

            assertThrows<Exception>("Creation of a new branch should error when the branch creation request fails.") {
                createNewBranch(baseBranch = branch, newBranch = newBranch)
            }
        }
    }

    @Nested
    inner class TestDeleteRiSc {
        private val pathToBranchCreationEndpoint = "/$owner/$repository/git/refs"
        private val pathToRepositoryInfoEndpoint = "/$owner/$repository"

        private fun pathToGetLastCommitOnBranch(branch: String) = "/$owner/$repository/commits/heads/$branch"

        private fun pathToDraftRiScContent(riScId: String) =
            "/$owner/$repository/contents/$riscFolderPath/${riScFilename(riScId)}?ref=$riScId"

        private fun pathToRiScContent(riScId: String) = "/$owner/$repository/contents/$riscFolderPath/${riScFilename(riScId)}"

        private fun pathToDeleteRiScContent(riScId: String) = "/$owner/$repository/contents/$riscFolderPath/${riScFilename(riScId)}"

        private fun pathToDeleteBranch(branch: String) = "/$owner/$repository/git/refs/heads/$branch"

        private fun queueContentResponse(
            riScId: String,
            sha: String,
            path: String,
        ) = webClient.queueResponse(
            response = mockableResponseFromObject(GithubFileDTO(content = "{}", sha = sha, name = riScFilename(riScId))),
            path = path,
        )

        private fun queuePublishedContentResponse(
            riScId: String,
            sha: String,
        ) = queueContentResponse(riScId = riScId, sha = sha, path = pathToRiScContent(riScId))

        private fun queueDraftContentResponse(
            riScId: String,
            sha: String,
        ) = queueContentResponse(riScId = riScId, sha = sha, path = pathToDraftRiScContent(riScId))

        private fun queueRepositoryInfoResponse(defaultBranch: String) =
            webClient.queueResponse(
                response =
                    mockableResponseFromObject(
                        GithubRepositoryDTO(
                            defaultBranch = defaultBranch,
                            permissions =
                                GithubRepositoryPermissions(admin = false, maintain = false, push = true, triage = false, pull = true),
                        ),
                    ),
                path = pathToRepositoryInfoEndpoint,
            )

        private fun queueDeleteDraftFileResponse(riScId: String) {
            val sha = randomSHA()
            webClient.queueResponse(
                response =
                    MockableResponse(
                        /* A response string mimicking parts of the response from the GitHub API. The response is not
                           processed in the application beyond a string. There is thus no datatype to use for the answer. */
                        content =
                            """
                            {
                                "content": null,
                                "commit": {
                                    "sha": "$sha",
                                    "message": "Deleted RiSc with id: $riScId requires new approval"
                                    "url": "https://api.github.com/repos/$owner/$repository/git/commits/$sha", 
                                }
                            }
                            """.trimIndent(),
                    ),
                path = pathToDeleteRiScContent(riScId),
                method = HttpMethod.DELETE,
            )
        }

        private fun deleteRiSc(riScId: String) =
            runBlocking {
                githubConnector.deleteRiSc(
                    owner = owner,
                    repository = repository,
                    accessToken = "accessToken",
                    riScId = riScId,
                )
            }

        @Test
        fun `test delete unpublished RiSc`() {
            val riScId = randomRiSc()
            val unpublishedSHA = randomSHA()
            val defaultBranch = "default"

            queueDraftContentResponse(riScId, unpublishedSHA)
            queueRepositoryInfoResponse(defaultBranch)

            webClient.queueResponse(
                response = MockableResponse(content = null, httpStatus = HttpStatus.NOT_FOUND),
                path = pathToRiScContent(riScId),
            )

            webClient.queueResponse(
                response = MockableResponse(content = null, httpStatus = HttpStatus.NO_CONTENT),
                path = pathToDeleteBranch(riScId),
                method = HttpMethod.DELETE,
            )

            val response = deleteRiSc(riScId)

            assertEquals(
                ProcessingStatus.DeletedRiSc,
                response.status,
                "When the RiSc has not been published, it should be properly deleted.",
            )

            val deleteResponse = webClient.getNextRequest(pathToDeleteBranch(riScId))

            assertEquals(HttpMethod.DELETE, deleteResponse.method, "The delete endpoint should have been called with the DELETE method.")
        }

        @Test
        fun `test delete unpublished RiSc throws exception on error`() {
            val riScId = randomRiSc()
            val unpublishedSHA = randomSHA()
            val defaultBranch = "main"

            queueDraftContentResponse(riScId, unpublishedSHA)
            queueRepositoryInfoResponse(defaultBranch)

            webClient.queueResponse(
                response = MockableResponse(content = null, httpStatus = HttpStatus.NOT_FOUND),
                path = pathToRiScContent(riScId),
            )

            webClient.queueResponse(
                response = MockableResponse(content = null, httpStatus = HttpStatus.CONFLICT),
                path = pathToDeleteBranch(riScId),
                method = HttpMethod.DELETE,
            )

            assertThrows<DeletingRiScException>(
                "If the GitHub API returns an error on deletion, the method should throw a DeletingRiScException.",
            ) { deleteRiSc(riScId) }
        }

        @Test
        fun `test delete published RiSc with existing branch`() {
            val riScId = randomRiSc()
            val unpublishedSHA = randomSHA()
            val publishedSHA = randomSHA()
            val defaultBranch = "base"

            queueDraftContentResponse(riScId, unpublishedSHA)
            queueRepositoryInfoResponse(defaultBranch)
            queuePublishedContentResponse(riScId, publishedSHA)
            queueDeleteDraftFileResponse(riScId)

            val response = deleteRiSc(riScId)

            assertEquals(
                ProcessingStatus.DeletedRiScRequiresApproval,
                response.status,
                "When the RiSc has been published, it should require an approval before being deleted.",
            )

            val deleteRequestContent =
                webClient
                    .getNextRequest(
                        path = pathToDeleteRiScContent(riScId),
                        method = HttpMethod.DELETE,
                    ).deserializeContent<GithubDeleteFilePayload>()

            assertEquals(riScId, deleteRequestContent.branch, "The RiSc should be deleted on its draft branch.")
            assertEquals(
                unpublishedSHA,
                deleteRequestContent.sha,
                "The SHA used should be equal to the SHA for the RiSc on the draft branch.",
            )
        }

        @Test
        fun `test delete published RiSc without existing branch`() {
            val riScId = randomRiSc()
            val publishedSHA = randomSHA()
            // Last commit on default branch
            val commitSHA = randomSHA()
            val defaultBranch = "main"

            webClient.queueResponse(
                response = MockableResponse(content = null, httpStatus = HttpStatus.NOT_FOUND),
                path = pathToDraftRiScContent(riScId),
            )
            queueRepositoryInfoResponse(defaultBranch)
            queuePublishedContentResponse(riScId, publishedSHA)
            queueDeleteDraftFileResponse(riScId)

            webClient.queueResponse(
                response =
                    MockableResponse(
                        content =
                            """
                            {
                              "ref": "refs/heads/$riScId",
                              "url": "https://api.github.com/repos/$owner/$repository/git/refs/heads/$riScId",
                              "object": {
                                "type": "commit",
                                "sha": "$commitSHA",
                                "url": "https://api.github.com/repos/$owner/$repository/git/commits/$commitSHA"
                              }
                            }
                            """.trimIndent(),
                    ),
                path = pathToBranchCreationEndpoint,
            )

            webClient.queueResponse(
                response =
                    mockableResponseFromObject(
                        GithubCommitObject(
                            sha = commitSHA,
                            url = "https://api.github.com/repos/$owner/$repository/git/commits/$commitSHA",
                            commit =
                                GithubCommitInformation(
                                    message = "Updated code",
                                    committer =
                                        GithubCommitter(
                                            date = OffsetDateTime.now().minusHours(4),
                                            name = "username",
                                        ),
                                ),
                        ),
                    ),
                path = pathToGetLastCommitOnBranch(defaultBranch),
            )

            val response = deleteRiSc(riScId)

            assertEquals(
                ProcessingStatus.DeletedRiScRequiresApproval,
                response.status,
                "When the RiSc has been published, it should require an approval before being deleted.",
            )

            val branchRequestContent =
                webClient.getNextRequest(path = pathToBranchCreationEndpoint).deserializeContent<GithubCreateNewBranchPayload>()

            assertEquals(
                commitSHA,
                branchRequestContent.shaToBranchFrom,
                "The new branch should be created from the last commit on the default branch.",
            )
            assertEquals(
                "refs/heads/$riScId",
                branchRequestContent.nameOfNewBranch,
                "A new branch should be created for the riSc.",
            )

            val deleteRequestContent =
                webClient
                    .getNextRequest(
                        path = pathToDeleteRiScContent(riScId),
                        method = HttpMethod.DELETE,
                    ).deserializeContent<GithubDeleteFilePayload>()

            assertEquals(riScId, deleteRequestContent.branch, "The RiSc should be deleted on its draft branch.")
            assertEquals(
                publishedSHA,
                deleteRequestContent.sha,
                "The SHA used should be equal to the SHA for the RiSc on the default branch, as no changes were made after branching.",
            )
        }

        @Test
        fun `test delete published RiSc throws exception on error`() {
            val riScId = randomRiSc()
            val unpublishedSHA = randomSHA()
            val publishedSHA = randomSHA()
            val defaultBranch = "base"

            queueDraftContentResponse(riScId, unpublishedSHA)
            queueRepositoryInfoResponse(defaultBranch)
            queuePublishedContentResponse(riScId, publishedSHA)

            webClient.queueResponse(
                response = MockableResponse(content = null, httpStatus = HttpStatus.SERVICE_UNAVAILABLE),
                path = pathToDeleteRiScContent(riScId),
                method = HttpMethod.DELETE,
            )

            assertThrows<DeletingRiScException> (
                "Deletion of the RiSc should throw a DeletingRiScException when the GitHub API endpoint call fails.",
            ) { deleteRiSc(riScId) }
        }
    }
}
