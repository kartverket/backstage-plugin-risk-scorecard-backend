package no.risc.infra.connector

import no.risc.config.CryptoServiceConfig
import org.springframework.stereotype.Component

@Component
class CryptoServiceConnector(
    cryptoServiceConfig: CryptoServiceConfig,
) : WebClientConnector(cryptoServiceConfig.baseUrl)