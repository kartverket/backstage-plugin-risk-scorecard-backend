package no.risc.github

import MockableResponse
import MockableWebClient
import deserializeContent
import io.mockk.every
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import mockableResponseFromObject
import no.risc.exception.exceptions.CreatePullRequestException
import no.risc.exception.exceptions.PermissionDeniedOnGitHubException
import no.risc.github.models.GithubCommitInformation
import no.risc.github.models.GithubCommitObject
import no.risc.github.models.GithubCommitter
import no.risc.github.models.GithubCreateNewPullRequestPayload
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
import no.risc.risc.LastPublished
import no.risc.risc.RiScIdentifier
import no.risc.risc.RiScStatus
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

    private fun pathToRiSC(riScId: String) = "$riscFolderPath/${riScFilename(riScId)}"

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

    @Nested
    inner class TestPullRequests {
        val pathToPullRequestEndpoint = "/$owner/$repository/pulls"

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
                        head = GithubPullRequestBranch(ref = "risc-aaaab"),
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

        @Test
        fun `test create pull request for RiSc`() {
            val riScId = "aaaab"
            val baseBranch = "main"

            val pullRequest =
                GithubPullRequestObject(
                    url = "https://api.github.com/repos/$owner/$repository/pulls/29",
                    title = "Updated risk scorecard",
                    createdAt = OffsetDateTime.now(),
                    head = GithubPullRequestBranch("risc-$riScId"),
                    base = GithubPullRequestBranch("main"),
                    number = 37,
                )

            webClient.queueResponse(
                response = mockableResponseFromObject(pullRequest),
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
                        baseBranch = baseBranch,
                    )
                }

            assertEquals(pullRequest, result, "The create pull request should be returned.")

            val request = webClient.getNextRequest()

            val requestContent = request.deserializeContent<GithubCreateNewPullRequestPayload>()

            assertEquals(baseBranch, requestContent.base, "PR should be to the provided base branch.")
            assertEquals("$owner:$riScId", requestContent.head, "PR should be from the RiSc branch.")
        }

        @Test
        fun `test create pull request for RiSc throws exception on error`() {
            webClient.queueResponse(
                response = MockableResponse(content = null, httpStatus = HttpStatus.BAD_REQUEST),
                path = pathToPullRequestEndpoint,
            )

            assertThrows<CreatePullRequestException> {
                runBlocking {
                    githubConnector.createPullRequestForRiSc(
                        owner = owner,
                        repository = repository,
                        riScId = "aaaaa",
                        requiresNewApproval = false,
                        gitHubAccessToken = "access token",
                        userInfo = UserInfo(name = "Ola Nordmann", email = "ola.nordmann@test.com"),
                        baseBranch = "main",
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
                    riScId = "$filenamePrefix-$riScId",
                )
            }

        @Test
        fun `test fetch last published RiSc date and commit number`() {
            val riScId = "aaaaa"
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
                path = pathToRiScCommits(riScId),
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
                path = pathToDefaultBranchCommitsSince(lastCommitTime),
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
            val riScId = "aaaab"
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
            val riScId = "aaaac"
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
            val filePath = pathToRiSC("aaaaa")
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

            assertEquals(responseString, response)

            // Ignore file info request
            webClient.getNextRequest()

            val requestBody = webClient.getNextRequest().deserializeContent<GithubWriteToFilePayload>()
            assertNull(requestBody.sha, "The SHA should be null if the file does not already exist.")
        }

        @Test
        fun `test file update with put file request`() {
            val filePath = pathToRiSC("aaaab")
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

            assertEquals(responseString, response)

            // Ignore file info request
            webClient.getNextRequest()

            val requestBody = webClient.getNextRequest().deserializeContent<GithubWriteToFilePayload>()
            assertEquals(fileSHA, requestBody.sha, "The SHA should be equal to the file SHA if the file already exist.")
        }

        @Test
        fun `test put file request throws exception on error`() {
            val filePath = pathToRiSC("aaaac")
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
}
