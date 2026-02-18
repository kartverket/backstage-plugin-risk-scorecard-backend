package no.risc.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "init-risc")
class InitRiScServiceConfig {
    lateinit var baseUrl: String
    lateinit var repoOwner: String
    lateinit var repoName: String
}
