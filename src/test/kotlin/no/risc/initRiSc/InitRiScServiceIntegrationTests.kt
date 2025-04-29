package no.risc.initRiSc

import MockableResponse
import MockableWebClient
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.risc.exception.exceptions.SopsConfigGenerateFetchException
import no.risc.infra.connector.InitRiScServiceConnector
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class InitRiScServiceIntegrationTests {
    private lateinit var initRiScServiceIntegration: InitRiScServiceIntegration
    private lateinit var webClient: MockableWebClient

    @BeforeEach
    fun beforeEach() {
        webClient = MockableWebClient()
        initRiScServiceIntegration =
            InitRiScServiceIntegration(
                initRiScServiceConnector = mockk<InitRiScServiceConnector>().also { every { it.webClient } returns webClient.webClient },
            )
    }

    @Test
    fun `test generate default RiSc throws SopsConfigGenerateFetchException when no content is received`() {
        webClient.queueResponse(MockableResponse(content = null))

        assertThrows<SopsConfigGenerateFetchException> {
            runBlocking {
                initRiScServiceIntegration.generateDefaultRiSc("")
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
                initRiScServiceIntegration.generateDefaultRiSc("""{ "title": "title", "scope": "scope"}""")
            }

        assertEquals(
            riSc,
            response,
            "The returned default RiSc should be equivalent to the one returned from the initialize-risc API endpoint.",
        )
    }
}
