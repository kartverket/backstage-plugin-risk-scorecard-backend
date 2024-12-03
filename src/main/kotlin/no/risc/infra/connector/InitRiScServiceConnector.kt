package no.risc.infra.connector

import no.risc.config.InitRiScServiceConfig
import org.springframework.stereotype.Component

@Component
class InitRiScServiceConnector(
    val initRiScServiceConfig: InitRiScServiceConfig,
) : WebClientConnector(baseURL = initRiScServiceConfig.baseUrl)
