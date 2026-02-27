package no.risc.initRiSc

import MockableResponse
import MockableWebClient
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.risc.exception.exceptions.SopsConfigGenerateFetchException
import no.risc.infra.connector.InitRiScServiceConnector
import no.risc.infra.connector.models.AccessTokens
import no.risc.infra.connector.models.GCPAccessToken
import no.risc.infra.connector.models.GithubAccessToken
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class InitRiScServiceAirtableImplTests {
    private lateinit var initRiScServiceAirtableImpl: InitRiScServiceAirtableImpl
    private lateinit var webClient: MockableWebClient

    @BeforeEach
    fun beforeEach() {
        webClient = MockableWebClient()
        initRiScServiceAirtableImpl =
            InitRiScServiceAirtableImpl(
                initRiScServiceConnector = mockk<InitRiScServiceConnector>().also { every { it.webClient } returns webClient.webClient },
            )
    }

    @Test
    fun `test generate default RiSc throws SopsConfigGenerateFetchException when no content is received`() {
        webClient.queueResponse(MockableResponse(content = null))

        assertThrows<SopsConfigGenerateFetchException> {
            runBlocking {
                initRiScServiceAirtableImpl.getInitRiSc(
                    "id1",
                    "",
                    AccessTokens(
                        GithubAccessToken("gh_access_token"),
                        GCPAccessToken("gcp_access_token"),
                    ),
                )
            }
        }
    }

    @Test
    fun `test generate default RiSc returns generated RiSc string`() {
        val riSc =
            """
            {
              "schemaVersion": "4.0",
              "title": "Transformasjon",
              "scope": "Sikkerhet av backend.",
              "valuations": [],
              "scenarios": []
            }
            """.trimIndent()

        webClient.queueResponse(MockableResponse(content = riSc))

        val response =
            runBlocking {
                initRiScServiceAirtableImpl.getInitRiSc(
                    "id1",
                    """{ "title": "title", "scope": "scope"}""",
                    AccessTokens(
                        GithubAccessToken("gh_access_token"),
                        GCPAccessToken("gcp_access_token"),
                    ),
                )
            }

        assertEquals(
            riSc,
            response,
            "The returned default RiSc should be equivalent to the one returned from the initialize-risc API endpoint.",
        )
    }
}
