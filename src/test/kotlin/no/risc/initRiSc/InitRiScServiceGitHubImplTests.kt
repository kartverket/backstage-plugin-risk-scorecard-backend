package no.risc.initRiSc

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
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
            initRiScRepoName = "name",
            initRiScRepoOwner = "owner",
            githubConnector = mockedGithubConnector,
        )

    companion object {
        val GITHUB_RESPONSE_DATA1 =
            """
            schemaVersion: "5.2"
            title: Initiell RoS - web-app
            scope: Denne RoS'en er generert fra opplysninger...
            scenarios:
              - title: Produktet mangler eller bryter avtaler med tredjeparter
                scenario:
                  ID: QQrIA
                  description: Produktet/tjenesten oppfyller ikke inngåtte avtaler, eller det mangler avtaler eller avtaleinnhold som burde vært på plass. Avtaler som burde vært avsluttet, blir likevel videreført. Tredjeparter kan være datakonsumenter, klienter, datatilbydere og plattformleverandører
                  threatActors:
                    - Reckless employee
                  vulnerabilities:
                    - Misconfiguration
                    - Flawed design
                  risk:
                    consequence: 1e+06
                    probability: 1
                  actions:
                    - title: Inngå sikkerhetsavtale
                      action:
                        ID: cvqIP
                        description: "Inngå sikkerhetsavtale med underleverandør (f.eks skytjenester) hvis det behandles sikkerhetsgraderte data (altså utover skjermingsverdig ugradert).\n\n```\nInternt metodeverk:   AR-005\nISO 27002:            5.20\nNIST CSF 2.0:         GV.OC-03, GV.SC-05\nOWASP ASVS 4.0.3:     \nNSMs grunnprinsipper: \nSikkerhetsloven:      § 9-2\n```"
                        status: Not OK
                        url: ""
                  remainingRisk:
                    consequence: 0
                    probability: 0
              - title: Annet scenario
                scenario:
                  ID: QQrIA2
                  description: Beskrivelse
                  threatActors:
                    - Reckless employee
                  vulnerabilities:
                    - Misconfiguration
                  risk:
                    consequence: 0
                    probability: 0
                  actions:
                    - title: Inngå sikkerhetsavtale
                      action:
                        ID: cvqIP2
                        description: "beskrivelse"
                        status: Not OK
                        url: ""
                    - title: Inngå sikkerhetsavtale V2
                      action:
                        ID: cvqIP3
                        description: "beskrivelse"
                        status: Not OK
                        url: ""
                  remainingRisk:
                    consequence: 0
                    probability: 0
            """.trimIndent()

        val GITHUB_RESPONSE_DATA2 =
            """
            ---
            schemaVersion: "5.2"
            title: "ros"
            scope: "scope"
            scenarios:
            - title: "scn1"
              scenario:
                ID: "FZ6m3"
                description: "scenario"
                threatActors: []
                vulnerabilities: []
                risk:
                  summary: ""
                  probability: 0.0025
                  consequence: 8000.0
                remainingRisk:
                  summary: ""
                  probability: 0.0025
                  consequence: 8000.0
                actions:
                - title: "act1"
                  action:
                    ID: "n8dB5"
                    description: ""
                    url: ""
                    status: "Not relevant"
                    lastUpdated: "2026-02-13T14:43:50.92Z"
                    lastUpdatedBy: ""
                - title: "act2"
                  action:
                    ID: "56FG2"
                    description: "litt data"
                    url: "https://vg.no"
                    status: "OK"
                    lastUpdated: "2026-02-13T14:43:50.92Z"
                    lastUpdatedBy: ""
                - title: "act3"
                  action:
                    ID: "4bI3N"
                    description: ""
                    url: ""
                    status: "Not OK"
                    lastUpdated: "2026-02-13T15:10:49.046Z"
                    lastUpdatedBy: "Kari Nordmann"
                - title: "act4"
                  action:
                    ID: "ncds2"
                    description: ""
                    url: ""
                    status: "Not OK"
                - title: "act5"
                  action:
                    ID: "owkowckowk"
                    description: ""
                    url: ""
                    status: "Not OK"
                    lastUpdated: ""
                - title: "act6"
                  action:
                    ID: "jjjj"
                    description: ""
                    url: ""
                    status: "Not OK"
                    lastUpdated: null


            """.trimIndent()

        val INITIAL_CONTENT1 =
            """
            {
              "schemaVersion":"5.2",
              "title":"Title from initialContent",
              "scope":"Scope from initialContent",
              "scenarios":[]
            }
            """.trimIndent()

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
                    data =
                        """
                        [
                          {
                            "id": "risc-example",
                            "priorityIndex": 1,
                            "listName": "RiSc Example",
                            "listDescription": "Desc",
                            "preferredBackstageComponentType": "service"
                          }
                        ]
                        """.trimIndent(),
                    status = GithubStatus.Success,
                )

            coEvery {
                mockedGithubConnector.fetchPublishedRiSc("owner", "name", "risc-example", any<String>())
            } returns
                GithubContentResponse(data = GITHUB_RESPONSE_DATA1, status = GithubStatus.Success)

            val descriptors =
                initRiScServiceGitHubImpl.getInitRiScDescriptors(ACCESS_TOKEN)
            val descriptor = descriptors[0]

            assertEquals(1, descriptors.size)
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
                mockedGithubConnector.fetchPublishedRiSc("owner", "name", "risc-example", any<String>())
            } returns
                GithubContentResponse(data = GITHUB_RESPONSE_DATA1, status = GithubStatus.Success)

            val initRiSc =
                initRiScServiceGitHubImpl.getInitRiSc(
                    "risc-example",
                    INITIAL_CONTENT1,
                    ACCESS_TOKEN,
                )

            val initRiScParsed = RiSc.fromContent(initRiSc)
            assertTrue(initRiScParsed is RiSc5X)
            val initRiSc5X = initRiScParsed as RiSc5X

            assertEquals("Title from initialContent", initRiSc5X.title)
            assertEquals("Scope from initialContent", initRiSc5X.scope)
            assertEquals(2, initRiSc5X.scenarios.size)
        }

    @Test
    fun `getInitRiSc returns a RiSc without lastUpdatedBy, lastUpdated, and action statuses set to NOT OK`() =
        runTest {
            coEvery {
                mockedGithubConnector.fetchPublishedRiSc("owner", "name", "risc-with-data", any<String>())
            } returns
                GithubContentResponse(data = GITHUB_RESPONSE_DATA2, status = GithubStatus.Success)

            val initRiSc = initRiScServiceGitHubImpl.getInitRiSc("risc-with-data", INITIAL_CONTENT1, ACCESS_TOKEN)
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
