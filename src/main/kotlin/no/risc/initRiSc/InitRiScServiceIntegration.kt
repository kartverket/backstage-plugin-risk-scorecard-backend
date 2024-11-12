package no.risc.initRiSc

import no.risc.infra.connector.InitRiScServiceConnector
import no.risc.initRiSc.model.GenerateRiScRequestBody
import no.risc.initRiSc.model.GenerateRiScResponseBody
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.bodyToMono

@Component
class InitRiScServiceIntegration(
    val initRiScServiceConnector: InitRiScServiceConnector,
) {
    fun getInitialRiScAndSopsConfig(
        repositoryName: String,
        publicAgeKey: String?,
        gcpProjectId: String,
    ): GenerateRiScResponseBody? {
        val r =
            initRiScServiceConnector.webClient
                .post()
                .uri("/generate/$repositoryName")
                .body(BodyInserters.fromValue(GenerateRiScRequestBody(publicAgeKey, gcpProjectId)))
                .retrieve()
                .bodyToMono<GenerateRiScResponseBody>()

        return r
            .block()
    }
}
