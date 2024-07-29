package no.risc.infra.connector

import org.springframework.stereotype.Component

@Component
class CryptoServiceConnector(
    baseUrl: String = "http://localhost:8084",
) : WebClientConnector(baseUrl) {

}