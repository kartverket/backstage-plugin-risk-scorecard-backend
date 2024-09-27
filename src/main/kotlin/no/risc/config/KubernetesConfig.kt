package no.risc.config

import io.kubernetes.client.openapi.apis.BatchV1Api
import io.kubernetes.client.openapi.apis.CoreV1Api
import io.kubernetes.client.util.Config
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "kubernetes")
class KubernetesConfig {

    lateinit var namespace: String

    fun coreV1Api() = CoreV1Api(Config.fromCluster())
    fun batchV1Api() = BatchV1Api(Config.fromCluster())

}