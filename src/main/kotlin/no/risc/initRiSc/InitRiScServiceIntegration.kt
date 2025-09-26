package no.risc.initRiSc

import no.risc.exception.exceptions.SopsConfigGenerateFetchException
import no.risc.infra.connector.InitRiScServiceConnector
import no.risc.initRiSc.model.GenerateRiScRequestBody
import no.risc.risc.models.DefaultRiScType
import no.risc.risc.models.ProcessRiScResultDTO
import no.risc.risc.models.ProcessingStatus
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
     *
     * @param defaultRiScTypes A list of predefined default RiSc types to generate the RiSc from. Currently, only a
     *                         single default RiSc is supported. Therefore, the first RiSc from the defaultRiScTypes
     *                         list is selected for the RiSc generation.
     */
    suspend fun generateDefaultRiSc(
        initialRiSc: String,
        defaultRiScTypes: List<DefaultRiScType>,
    ): String =
        initRiScServiceConnector.webClient
            .post()
            .uri("/generate")
            .body(BodyInserters.fromValue(GenerateRiScRequestBody(initialRiSc, defaultRiScTypes)))
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
