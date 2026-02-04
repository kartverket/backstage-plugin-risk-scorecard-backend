package no.risc.initRiSc

import no.risc.exception.exceptions.FetchException
import no.risc.exception.exceptions.SopsConfigGenerateFetchException
import no.risc.infra.connector.InitRiScServiceConnector
import no.risc.initRiSc.model.GenerateRiScRequestBody
import no.risc.initRiSc.model.RiScTypeDescriptor
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
     * @param defaultRiScId ID of default RiSc to generate the RiSc from.
     */
    suspend fun generateDefaultRiSc(
        initialRiSc: String,
        defaultRiScId: String,
    ): String =
        initRiScServiceConnector.webClient
            .post()
            .uri("/generate")
            .body(BodyInserters.fromValue(GenerateRiScRequestBody(initialRiSc, defaultRiScId)))
            .retrieve()
            .awaitBodyOrNull<String>() ?: throw SopsConfigGenerateFetchException(
            "Failed to generate default RiSc for defaultRiScId=$defaultRiScId: " +
                    "empty response body from init-risc service (Airtable).",
            ProcessRiScResultDTO(
                riScId = "",
                status = ProcessingStatus.ErrorWhenCreatingRiSc,
                statusMessage = ProcessingStatus.ErrorWhenCreatingRiSc.message,
            ),
        )

    suspend fun fetchDefaultRiScTypeDescriptors(): List<RiScTypeDescriptor> {
        val descriptors =
            initRiScServiceConnector.webClient
                .get()
                .uri("/descriptors")
                .retrieve()
                .awaitBodyOrNull<List<RiScTypeDescriptor>>()

        if (descriptors == null) {
            throw FetchException("Failed to fetch RiSc type descriptors: empty response body from init-risc service (Airtable).", ProcessingStatus.FailedToFetchFromAirtable)
        }
        return descriptors
    }
}
