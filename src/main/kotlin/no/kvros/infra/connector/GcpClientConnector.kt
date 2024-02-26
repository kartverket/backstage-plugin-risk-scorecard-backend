package no.kvros.infra.connector

import com.google.cloud.secretmanager.v1.SecretManagerServiceClient

class GcpClientConnector {
    fun getSecretValue(secretName: String): String? {
        val client = SecretManagerServiceClient.create()
        return try {
            val clientResponse = client.accessSecretVersion(secretName)
            val payload = clientResponse.payload.data.toStringUtf8()
            payload
        } catch (e: Exception) {
            println(e)
            null
        } finally {
            client.close()
        }
    }

}