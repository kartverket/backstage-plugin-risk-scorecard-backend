package no.risc.infra.connector

import org.springframework.stereotype.Component

@Component
class GcpCloudResourceApiConnector(
    baseUrl: String = "https://cloudresourcemanager.googleapis.com",
) : WebClientConnector(baseUrl)
