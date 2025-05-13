package no.risc.github

import MockableResponse
import MockableWebClient
import io.mockk.every
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import mockableResponseFromObject
import no.risc.exception.exceptions.PermissionDeniedOnGitHubException
import no.risc.github.models.GithubFileDTO
import no.risc.github.models.GithubPullRequestBranch
import no.risc.github.models.GithubPullRequestObject
import no.risc.github.models.GithubReferenceObjectDTO
import no.risc.github.models.GithubRepositoryDTO
import no.risc.github.models.GithubRepositoryPermissions
import no.risc.github.models.GithubStatus
import no.risc.infra.connector.models.GitHubPermission
import no.risc.risc.RiScIdentifier
import no.risc.risc.RiScStatus
import no.risc.utils.encodeBase64
import no.risc.utils.generateRandomAlphanumericString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import java.time.OffsetDateTime

class GithubConnectorTests {
    private lateinit var githubConnector: GithubConnector
    private lateinit var webClient: MockableWebClient
    private val filenamePostfix = "risc"
    private val filenamePrefix = "risc"
    private val riscFolderPath = ".security/risc"
    private val owner = "owner"
    private val repository = "risc-repo"

    @BeforeEach
    fun beforeEach() {
        webClient = MockableWebClient()
        githubConnector =
            spyk(
                GithubConnector(
                    filenamePrefix = filenamePrefix,
                    filenamePostfix = filenamePostfix,
                    riScFolderPath = riscFolderPath,
                    githubHelper =
                        GithubHelper(
                            filenamePrefix = filenamePrefix,
                            filenamePostfix = filenamePostfix,
                            riScFolderPath = riscFolderPath,
                        ),
                ),
            )
        every { githubConnector.webClient } returns webClient.webClient
    }

    private fun randomSHA(): String = generateRandomAlphanumericString(41)

    private fun riScFilename(riScId: String) = "$filenamePrefix-$riScId.$filenamePostfix.yaml"

    @Nested
    inner class TestFetchAllRiScIdentifiers {
        private val pathToDraftIdentifiers = "/$owner/$repository/git/matching-refs/heads/$filenamePrefix-"
        private val pathToRiScFiles = "/$owner/$repository/contents/$riscFolderPath"
        private val pathToOpenPullRequests = "/$owner/$repository/pulls"

        private fun queueRiScResponses(
            draftedRiScIDs: List<String>,
            publishedRiScIDs: List<String>,
            approvedRiScIDs: List<String>,
        ) {
            webClient.queueResponse(
                response =
                    mockableResponseFromObject(
                        draftedRiScIDs.map { riScID ->
                            GithubReferenceObjectDTO(
                                ref = "refs/heads/$filenamePrefix-$riScID",
                                url = "https://api.github.com/repos/$owner/$repository/git/refs/heads/$filenamePrefix-$riScID",
                            )
                        },
                    ),
                path = pathToDraftIdentifiers,
            )

            webClient.queueResponse(
                response =
                    mockableResponseFromObject(
                        publishedRiScIDs.map { riScID ->
                            GithubFileDTO(
                                content = "{}",
                                sha = randomSHA(),
                                name = "$filenamePrefix-$riScID.$filenamePostfix.yaml",
                            )
                        },
                    ),
                path = pathToRiScFiles,
            )

            webClient.queueResponse(
                response =
                    mockableResponseFromObject(
                        approvedRiScIDs.mapIndexed { index, riScID ->
                            GithubPullRequestObject(
                                url = "https://api.github.com/repos/$owner/$repository/pulls/$index",
                                title = "Update RiSc",
                                createdAt = OffsetDateTime.now(),
                                head = GithubPullRequestBranch("$filenamePrefix-$riScID"),
                                base = GithubPullRequestBranch("main"),
                                number = index,
                            )
                        },
                    ),
                path = pathToOpenPullRequests,
            )
        }

        private fun fetchRiScIdentifiers(): List<RiScIdentifier> =
            runBlocking {
                githubConnector.fetchAllRiScIdentifiersInRepository(
                    owner = owner,
                    repository = repository,
                    accessToken = "accessToken",
                )
            }

        @Test
        fun `test fetch all risc identifiers in repository`() {
            val draftedRiScIDs = listOf("aaaaa", "aaaab")
            val publishedRiScIDs = listOf("bbbbb", "bbbbc")
            val approvedRiScIDs = listOf("ccccc", "ccccd")

            queueRiScResponses(draftedRiScIDs, publishedRiScIDs, approvedRiScIDs)

            val identifiers = fetchRiScIdentifiers()

            assertEquals(6, identifiers.size, "All unique risc identifiers should be found")
            assertTrue({
                publishedRiScIDs.all { riScID ->
                    identifiers.any { it.id == "$filenamePrefix-$riScID" && it.status == RiScStatus.Published }
                }
            }, "Unique published RiScs should be included in the list")
            assertTrue({
                draftedRiScIDs.all { riScID ->
                    identifiers.any { it.id == "$filenamePrefix-$riScID" && it.status == RiScStatus.Draft }
                }
            }, "Unique drafted RiScs should be included in the list")
            assertTrue({
                approvedRiScIDs.all { riScID ->
                    identifiers.any { it.id == "$filenamePrefix-$riScID" && it.status == RiScStatus.SentForApproval }
                }
            }, "Unique approved RiScs should be included in the list")
        }

        @Test
        fun `test fetch all risc identifiers in repository with overlap`() {
            val publishedRiScIDs = listOf("aaaaa", "bbbbb", "ddddd")
            val draftedRiScIDs = listOf("bbbbb", "ccccc")
            val approvedRiScIDs = listOf("ccccc", "ddddd")

            queueRiScResponses(draftedRiScIDs, publishedRiScIDs, approvedRiScIDs)

            val identifiers = fetchRiScIdentifiers()

            assertEquals(4, identifiers.size, "All unique risc identifiers should be found")
            assertTrue({
                identifiers.any { it.id == "$filenamePrefix-aaaaa" && it.status == RiScStatus.Published }
            }, "Unique published RiScs should be included in the list")
            assertTrue({
                identifiers.any { it.id == "$filenamePrefix-bbbbb" && it.status == RiScStatus.Draft }
            }, "Unique drafted RiScs should be included in the list")
            assertTrue({
                identifiers.any { it.id == "$filenamePrefix-ccccc" && it.status == RiScStatus.SentForApproval } &&
                    identifiers.any { it.id == "$filenamePrefix-ddddd" && it.status == RiScStatus.SentForApproval }
            }, "Unique approved RiScs should be included in the list")
        }

        @Test
        fun `test fetch all risc identifiers in repository with retrieval errors`() {
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

            val identifiers = fetchRiScIdentifiers()

            assertTrue(identifiers.isEmpty(), "Fetch all RiSc identifiers should fail gracefully on network errors.")
        }
    }

    @Nested
    inner class TestFetchRiscContent {
        private fun pathToRiScContent(riScId: String) = "/$owner/$repository/contents/$riscFolderPath/${riScFilename(riScId)}"

        private fun pathToRiScContentOnDraftBranch(riScId: String) =
            "/$owner/$repository/contents/$riscFolderPath/${riScFilename(riScId)}?ref=$filenamePrefix-$riScId"

        private fun fetchPublishedRiSc(riScId: String) =
            runBlocking {
                githubConnector.fetchPublishedRiSc(
                    owner = owner,
                    repository = repository,
                    id = "$filenamePrefix-$riScId",
                    accessToken = "accessToken",
                )
            }

        @Test
        fun `test fetch RiSc content`() {
            val riScId = "abcde"
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
            val riScId = "aaaaa"

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
            val riScId = "aaaaa"

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
                    id = "$filenamePrefix-$riScId",
                    accessToken = "accessToken",
                )
            }

        @Test
        fun `test fetch draft RiSc content`() {
            val riScId = "abcde"
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
            val riScId = "aaaaa"

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
            val riScId = "aaaaa"

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
}
