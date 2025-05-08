package no.risc.github

import MockableResponse
import MockableWebClient
import io.mockk.every
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import mockableResponseFromObject
import no.risc.github.models.GithubFileDTO
import no.risc.github.models.GithubPullRequestBranch
import no.risc.github.models.GithubPullRequestObject
import no.risc.github.models.GithubReferenceObjectDTO
import no.risc.risc.RiScStatus
import no.risc.utils.generateRandomAlphanumericString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
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
                                sha = generateRandomAlphanumericString(41),
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

        @Test
        fun `test fetch all risc identifiers in repository`() {
            val draftedRiScIDs = listOf("aaaaa", "aaaab")
            val publishedRiScIDs = listOf("bbbbb", "bbbbc")
            val approvedRiScIDs = listOf("ccccc", "ccccd")

            queueRiScResponses(draftedRiScIDs, publishedRiScIDs, approvedRiScIDs)

            val identifiers =
                runBlocking {
                    githubConnector.fetchAllRiScIdentifiersInRepository(
                        owner = owner,
                        repository = repository,
                        accessToken = "",
                    )
                }

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

            val identifiers =
                runBlocking {
                    githubConnector.fetchAllRiScIdentifiersInRepository(
                        owner = owner,
                        repository = repository,
                        accessToken = "",
                    )
                }

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

            val identifiers =
                runBlocking {
                    githubConnector.fetchAllRiScIdentifiersInRepository(
                        owner = owner,
                        repository = repository,
                        accessToken = "",
                    )
                }

            assertTrue(identifiers.isEmpty(), "Fetch all RiSc identifiers should fail gracefully on network errors.")
        }
    }
}
