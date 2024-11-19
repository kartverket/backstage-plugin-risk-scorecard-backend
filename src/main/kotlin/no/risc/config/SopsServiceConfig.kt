package no.risc.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "sops")
class SopsServiceConfig {
    lateinit var backendPublicKey: String
}
