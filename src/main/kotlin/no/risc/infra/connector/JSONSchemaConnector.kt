package no.risc.infra.connector

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class JSONSchemaConnector(
    @Value("\${json-schema-base-url}") baseUrl: String,
) : WebClientConnector(baseUrl) {
    fun fetchJSONSchema(schemaVersion: String): String? {
        return try {
            webClient.get()
                .uri("/ros_schema_no_v$schemaVersion.json")
                .header("Accept", "application/vnd.github.json")
                .retrieve()
                .bodyToMono(String::class.java)
                .block()
        } catch (e: Exception) {
            null
        }
    }
}
