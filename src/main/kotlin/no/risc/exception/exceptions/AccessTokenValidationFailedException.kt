package no.risc.exception.exceptions

import no.risc.risc.models.ProcessRiScResultDTO
import no.risc.risc.models.ProcessingStatus

data class AccessTokenValidationFailedException(
    val response: Any =
        ProcessRiScResultDTO(
            riScId = "",
            status = ProcessingStatus.AccessTokensValidationFailure,
            statusMessage = "An error occurred when validating access token.",
        ),
    override val message: String,
) : Exception()
