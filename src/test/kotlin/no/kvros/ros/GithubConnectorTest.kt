package no.kvros.ros

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GithubConnectorTest {

    private val githubConnector = GithubConnector()

    @Test
    fun `prov a poste noe nytt til et github-repo`() {

        githubConnector.writeToGithub(
            owner = "bekk",
            repository = "kv-ros-backend",
            accessToken = "accessToken",
            writePayload = GithubWritePayload(
                message = "Commit-melding for noe fra kotlin",
                content = Base64.getEncoder().encodeToString("Dette er min melding".toByteArray()),
            ),
            rosFilePath = "rosetiros.txt"
        )
    }
}