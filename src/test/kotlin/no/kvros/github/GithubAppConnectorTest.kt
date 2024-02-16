package no.kvros.github

import org.junit.jupiter.api.Test

class GithubAppConnectorTest {
    @Test
    fun `create jwt`() {
        val githubAppConnector = GithubAppConnector(
            GithubAppIdentifier(
                appId = 828331,
                installationId = 47304902
            )
        )
        val jwt = githubAppConnector.getGithubAppSignedJWT("something")
        val actual = githubAppConnector.getGithubAppAccessToken(jwt)
    }
}