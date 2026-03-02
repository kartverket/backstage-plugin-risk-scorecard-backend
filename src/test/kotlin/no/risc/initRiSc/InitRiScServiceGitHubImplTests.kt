package no.risc.initRiSc

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import no.risc.getResource
import no.risc.github.GithubConnector
import no.risc.github.models.GithubContentResponse
import no.risc.github.models.GithubStatus
import no.risc.infra.connector.models.AccessTokens
import no.risc.infra.connector.models.GCPAccessToken
import no.risc.infra.connector.models.GithubAccessToken
import no.risc.risc.models.RiSc
import no.risc.risc.models.RiSc5X
import no.risc.risc.models.RiScScenarioActionStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InitRiScServiceGitHubImplTests {
    val mockedGithubConnector: GithubConnector = mockk()

    private val initRiScServiceGitHubImpl: InitRiScServiceGitHubImpl =
        InitRiScServiceGitHubImpl(
            githubConnector = mockedGithubConnector,
        )

    companion object {
        val ACCESS_TOKEN =
            AccessTokens(
                GithubAccessToken("x"),
                GCPAccessToken("y"),
            )
    }

    @Test
    fun `getInitRiScDescriptors returns correct descriptors`() =
        runTest {
            coEvery { mockedGithubConnector.fetchInitRiScDescriptorConfigs(any()) } returns
                GithubContentResponse(
                    data = getResource("init-risc-descriptor-configs.json"),
                    status = GithubStatus.Success,
                )

            coEvery {
                mockedGithubConnector.fetchInitRiSc("risc-example", any<String>())
            } returns
                GithubContentResponse(data = getResource("github-response-data-1-v5.2.json"), status = GithubStatus.Success)

            coEvery {
                mockedGithubConnector.fetchInitRiSc("risc-example-x2", any<String>())
            } returns
                GithubContentResponse(data = getResource("github-response-data-2-v5.2.json"), status = GithubStatus.Success)

            val descriptors =
                initRiScServiceGitHubImpl.getInitRiScDescriptors(ACCESS_TOKEN)
            val descriptor = descriptors[0]

            assertEquals(2, descriptors.size)
            assertEquals("risc-example", descriptor.id)
            assertEquals("RiSc Example", descriptor.listName)
            assertEquals("Desc", descriptor.listDescription)
            assertEquals("Initiell RoS - web-app", descriptor.defaultTitle)
            assertEquals("Denne RoS'en er generert fra opplysninger...", descriptor.defaultScope)
            assertEquals(2, descriptor.numberOfScenarios)
            assertEquals(3, descriptor.numberOfActions)
            assertEquals("service", descriptor.preferredBackstageComponentType)
            assertEquals(1, descriptor.priorityIndex)
        }

    @Test
    fun `getInitRiSc returns the correct initial risc`() =
        runTest {
            coEvery {
                mockedGithubConnector.fetchInitRiSc("risc-example", any<String>())
            } returns
                GithubContentResponse(data = getResource("github-response-data-1-v5.2.json"), status = GithubStatus.Success)

            val initRiSc =
                initRiScServiceGitHubImpl.getInitRiSc(
                    "risc-example",
                    getResource("initial-content-1-v5.2.json"),
                    ACCESS_TOKEN,
                )

            val initRiScParsed = RiSc.fromContent(initRiSc)
            assertTrue(initRiScParsed is RiSc5X)
            val initRiSc5X = initRiScParsed as RiSc5X

            assertEquals("Title from initialContent", initRiSc5X.title)
            assertEquals("Scope from initialContent", initRiSc5X.scope)
            assertEquals(2, initRiSc5X.scenarios.size)
            assertEquals("Produktet mangler eller bryter avtaler med tredjeparter", initRiSc5X.scenarios[0].title)
            assertEquals(1, initRiSc5X.scenarios[0].actions.size)
            assertEquals("cvqIP", initRiSc5X.scenarios[0].actions[0].id)
        }

    @Test
    fun `getInitRiSc returns a RiSc without lastUpdatedBy, lastUpdated, and action statuses set to NOT OK`() =
        runTest {
            coEvery {
                mockedGithubConnector.fetchInitRiSc("risc-with-data", any<String>())
            } returns
                GithubContentResponse(data = getResource("github-response-data-2-v5.2.json"), status = GithubStatus.Success)

            val initRiSc = initRiScServiceGitHubImpl.getInitRiSc("risc-with-data", getResource("initial-content-1-v5.2.json"), ACCESS_TOKEN)
            val initRiScParsed = RiSc.fromContent(initRiSc)

            assertTrue(initRiScParsed is RiSc5X)
            val initRiSc5X = initRiScParsed as RiSc5X

            assertEquals("Title from initialContent", initRiSc5X.title)
            assertEquals("Scope from initialContent", initRiSc5X.scope)
            assertEquals(1, initRiSc5X.scenarios.size)
            assertEquals(6, initRiSc5X.scenarios[0].actions.size)

            initRiSc5X.scenarios[0].actions.forEach {
                assertEquals(RiScScenarioActionStatus.NOT_OK, it.status)
                assertEquals(null, it.lastUpdated)
                assertEquals(null, it.lastUpdatedBy)
            }
        }
}
