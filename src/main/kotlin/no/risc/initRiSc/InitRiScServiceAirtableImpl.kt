package no.risc.initRiSc

import no.risc.exception.exceptions.FetchException
import no.risc.exception.exceptions.SopsConfigGenerateFetchException
import no.risc.infra.connector.InitRiScServiceConnector
import no.risc.infra.connector.models.AccessTokens
import no.risc.initRiSc.model.GenerateRiScRequestBody
import no.risc.initRiSc.model.RiScTypeDescriptor
import no.risc.risc.models.ProcessRiScResultDTO
import no.risc.risc.models.ProcessingStatus
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.awaitBodyOrNull

@Primary
@Service
class InitRiScServiceAirtableImpl(
    private val initRiScServiceConnector: InitRiScServiceConnector,
) : InitRiScService {
    override suspend fun getInitRiSc(
        initRiScId: String,
        initialContent: String,
        accessTokens: AccessTokens,
    ): String =
        initRiScServiceConnector.webClient
            .post()
            .uri("/generate")
            .body(BodyInserters.fromValue(GenerateRiScRequestBody(initialContent, initRiScId)))
            .retrieve()
            .awaitBodyOrNull<String>() ?: throw SopsConfigGenerateFetchException(
            "Failed to generate default RiSc",
            ProcessRiScResultDTO(
                riScId = "",
                status = ProcessingStatus.ErrorWhenCreatingRiSc,
                statusMessage = ProcessingStatus.ErrorWhenCreatingRiSc.message,
            ),
        )

    override suspend fun getInitRiScDescriptors(accessTokens: AccessTokens): List<RiScTypeDescriptor> {
        val descriptors =
            initRiScServiceConnector.webClient
                .get()
                .uri("/descriptors")
                .retrieve()
                .awaitBodyOrNull<List<RiScTypeDescriptor>>()

        if (descriptors == null) {
            throw FetchException("Failed to fetch initial riScs from Airtable", ProcessingStatus.FailedToFetchFromAirtable)
        }
        return descriptors
    }
}
