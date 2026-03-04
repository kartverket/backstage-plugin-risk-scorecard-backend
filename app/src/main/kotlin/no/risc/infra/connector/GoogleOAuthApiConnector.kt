package no.risc.infra.connector

import org.springframework.stereotype.Component

@Component
class GoogleOAuthApiConnector(
    baseUrl: String = "https://oauth2.googleapis.com/tokeninfo",
) : WebClientConnector(baseUrl)
