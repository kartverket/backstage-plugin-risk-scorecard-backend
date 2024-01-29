package no.kvros.ros

import org.junit.jupiter.api.TestInstance
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GithubConnectorTest {

    private val githubConnector = GithubConnector()

    fun `prov a poste noe nytt til et github-repo`() {

        githubConnector.writeToFile(
            owner = "bekk",
            repository = "kv-ros-backend",
            path = "/",
            accessToken = "accessToken",
            writePayload = GithubWriteToFilePayload(
                message = "Commit-melding for noe fra kotlin",
                content = Base64.getEncoder().encodeToString("Dette er min melding".toByteArray()),
            ),
        )
    }
}