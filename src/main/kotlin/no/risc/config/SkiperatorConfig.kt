package no.risc.config

import io.kubernetes.client.openapi.apis.CustomObjectsApi
import io.kubernetes.client.util.Config
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import kotlin.properties.Delegates

@Configuration
@ConfigurationProperties(prefix = "skiperator")
class SkiperatorConfig {
    lateinit var namespace: String
    lateinit var imageUrl: String
    lateinit var externalSecretsName: String
    lateinit var riScBackendApplicationName: String
    fun customObjectsApi() = CustomObjectsApi(apiClient())
    fun apiClient() = Config.fromCluster()
}