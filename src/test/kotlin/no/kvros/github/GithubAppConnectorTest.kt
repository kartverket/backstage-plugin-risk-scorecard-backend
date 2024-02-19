package no.kvros.github

import no.kvros.infra.connector.MicrosoftAccessToken
import no.kvros.infra.connector.UserContext
import no.kvros.ros.ROSService
import no.kvros.ros.SimpleStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class GithubAppConnectorTest {

    private val gcpKeyResourcePath = System.getenv("GCP_KMS_RESOURCE_PATH")
    private val sopsAgePublicKey = System.getenv("SOPS_AGE_PUBLIC_KEY")
    private val rosUrl = System.getenv("ROS_URL")

    @Disabled
    @Test
    fun `create jwt`() {
        val githubConnector = GithubConnector(rosUrl)

        val rosService = ROSService(
            githubConnector,
            gcpKeyResourcePath,
            sopsAgePublicKey
        )

        val arbitraryUserContext = UserContext(
            microsoftAccessToken = MicrosoftAccessToken(""),
            githubAccessToken = GithubAppAccessToken("")
        )

        val actual = rosService.fetchROSFilenames("spire-test", "kv-ros-test-2", arbitraryUserContext)

        assertThat(actual.status).isEqualTo(SimpleStatus.Success)
    }
}