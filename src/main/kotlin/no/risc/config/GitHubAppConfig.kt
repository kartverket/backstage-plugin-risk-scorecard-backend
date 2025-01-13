package no.risc.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "github.app")
class GitHubAppConfig {
    lateinit var privateKey: String
    lateinit var installationId: String
    lateinit var id: String
}
