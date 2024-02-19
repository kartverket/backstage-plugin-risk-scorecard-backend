package no.kvros.infra.connector

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class GcpClientConnectorTest {

    @Test
    fun `get secret from GCP`() {
        val privateKeySecretName = "projects/457384642040/secrets/GITHUB_APP_PRIVATE_KEY"

        val clientConnector = GcpClientConnector()
        assertDoesNotThrow { clientConnector.getSecretValue(privateKeySecretName) }
    }
}