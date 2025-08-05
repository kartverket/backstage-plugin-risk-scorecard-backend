package no.risc.infra.connector
import no.risc.config.RosaConfig
import org.springframework.stereotype.Component

@Component
class RosaConnector(
    rosaConfig: RosaConfig,
) : WebClientConnector(baseURL = rosaConfig.baseUrl)
