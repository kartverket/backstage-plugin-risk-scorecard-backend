package no.kvros.github

import no.kvros.infra.connector.Email
import no.kvros.infra.connector.MicrosoftIdentifier
import no.kvros.infra.connector.User
import no.kvros.infra.connector.UserContext
import no.kvros.ros.ROSService
import no.kvros.ros.SimpleStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class GithubAppConnectorTest {

    private val gcpKeyResourcePath = System.getenv("GCP_KMS_RESOURCE_PATH")
    private val sopsAgePublicKey = System.getenv("SOPS_AGE_PUBLIC_KEY")
    private val githubAppId = System.getenv("GITHUB_APP_ID")
    private val githubInstallationId = System.getenv("GITHUB_APP_INSTALLATION_ID")
    private val gcpGithubPrivateKey = System.getenv("PRIVATE_KEY_SECRET_NAME")
    private val rosUrl = System.getenv("ROS_URL")

    @Disabled
    @Test
    fun `create jwt`() {
        val githubAppConnector = GithubAppConnector(
            appId = githubAppId.toInt(),
            installationId = githubInstallationId.toInt(),
            privateKeySecretName = gcpGithubPrivateKey
        )
        val githubConnector = GithubConnector(rosUrl)

        val rosService = ROSService(
            githubConnector,
            githubAppConnector,
            gcpKeyResourcePath,
            sopsAgePublicKey
        )

        val arbitraryUserContext = UserContext(
            microsoftIdentifier = MicrosoftIdentifier("", ""),
            user = User(email = Email(""), relationEntities = emptyList()),
            null
        )

        val actual = rosService.fetchROSFilenames("spire-test", "kv-ros-test-2", arbitraryUserContext)

        assertThat(actual.status).isEqualTo(SimpleStatus.Success)
    }
}