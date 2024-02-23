package no.kvros.infra.connector

import com.google.cloud.secretmanager.v1.SecretManagerServiceClient

class GcpClientConnector {
    val client = SecretManagerServiceClient.create()
    fun getSecretValue(secretName: String): String {
        return try {
            val clientResponse = client.accessSecretVersion(secretName)
            val payload = clientResponse.payload.data.toStringUtf8()
            client.close()
            payload
        } catch (e: Exception) {
            println(e)
            throw e
        }
    }

}