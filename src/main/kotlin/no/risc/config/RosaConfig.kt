package no.risc.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "rosa")
class RosaConfig {
    lateinit var baseUrl: String
}
