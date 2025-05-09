package no.risc.initRiSc

import no.risc.exception.exceptions.SopsConfigGenerateFetchException
import no.risc.infra.connector.InitRiScServiceConnector
import no.risc.initRiSc.model.GenerateRiScRequestBody
import no.risc.risc.ProcessRiScResultDTO
import no.risc.risc.ProcessingStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.awaitBodyOrNull

@Component
class InitRiScServiceIntegration(
    private val initRiScServiceConnector: InitRiScServiceConnector,
) {
    /**
     * Generates a default RiSc based on the passed initial RiSc using the external initialize RiSc service. Returns a
     * JSON serialized RiSc.
     *
     * @param initialRiSc A JSON serialized RiSc to base the default RiSc on. Must include the `title` and `scope`
     *                    fields. These are the only fields used from `initialRiSc`.
     */
    suspend fun generateDefaultRiSc(initialRiSc: String): String =
        initRiScServiceConnector.webClient
            .post()
            .uri("/generate")
            .body(BodyInserters.fromValue(GenerateRiScRequestBody(initialRiSc)))
            .retrieve()
            .awaitBodyOrNull<String>() ?: throw SopsConfigGenerateFetchException(
            "Failed to generate default RiSc",
            ProcessRiScResultDTO(
                riScId = "",
                status = ProcessingStatus.ErrorWhenCreatingRiSc,
                statusMessage = ProcessingStatus.ErrorWhenCreatingRiSc.message,
            ),
        )
}
