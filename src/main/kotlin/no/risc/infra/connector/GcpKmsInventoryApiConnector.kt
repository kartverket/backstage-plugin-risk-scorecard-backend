package no.risc.infra.connector

import org.springframework.stereotype.Component

@Component
class GcpKmsInventoryApiConnector(
    baseUrl: String = "https://kmsinventory.googleapis.com",
) : WebClientConnector(baseUrl)
