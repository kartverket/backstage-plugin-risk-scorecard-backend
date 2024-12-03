package no.risc.initRiSc

import no.risc.exception.exceptions.SopsConfigGenerateFetchException
import no.risc.infra.connector.InitRiScServiceConnector
import no.risc.initRiSc.model.GenerateRiScRequestBody
import no.risc.initRiSc.model.GenerateSopsConfigRequestBody
import no.risc.risc.ProcessRiScResultDTO
import no.risc.risc.ProcessingStatus
import no.risc.sops.model.GcpProjectId
import no.risc.sops.model.PublicAgeKey
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.bodyToMono

@Component
class InitRiScServiceIntegration(
    private val initRiScServiceConnector: InitRiScServiceConnector,
) {
    fun generateDefaultRiSc(
        repositoryName: String,
        initialRiSc: String,
    ): String =
        initRiScServiceConnector.webClient
            .post()
            .uri("/generate/$repositoryName")
            .body(BodyInserters.fromValue(GenerateRiScRequestBody(initialRiSc)))
            .retrieve()
            .bodyToMono<String>()
            .block() ?: throw SopsConfigGenerateFetchException(
            "Failed to generate default RiSc",
            ProcessRiScResultDTO("", ProcessingStatus.ErrorWhenCreatingRiSc, ProcessingStatus.ErrorWhenCreatingRiSc.message),
        )

    fun generateSopsConfig(
        gcpProjectId: GcpProjectId,
        publicAgeKeys: List<PublicAgeKey>,
    ): String =
        initRiScServiceConnector.webClient
            .post()
            .uri("/generate/sopsConfig")
            .body(BodyInserters.fromValue(GenerateSopsConfigRequestBody(gcpProjectId, publicAgeKeys)))
            .retrieve()
            .bodyToMono<String>()
            .block() ?: throw SopsConfigGenerateFetchException(
            "Failed to generate sops config by calling init risc service",
            ProcessRiScResultDTO("", ProcessingStatus.FailedToCreateSops, ProcessingStatus.FailedToCreateSops.message),
        )
}
