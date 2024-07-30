package no.risc.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "crypto-service")
class CryptoServiceConfig {
    lateinit var baseUrl: String
}
