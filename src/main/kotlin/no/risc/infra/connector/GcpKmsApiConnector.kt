package no.risc.infra.connector

import org.springframework.stereotype.Component

@Component
class GcpKmsApiConnector(
    baseUrl: String = "https://cloudkms.googleapis.com",
) : WebClientConnector(baseUrl)
