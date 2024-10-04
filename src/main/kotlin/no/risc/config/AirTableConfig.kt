package no.risc.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "airtable")
class AirTableConfig {
    lateinit var accessToken: String
    lateinit var baseId: String
}