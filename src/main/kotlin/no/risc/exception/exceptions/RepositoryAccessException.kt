package no.risc.exception.exceptions

import no.risc.risc.models.ProcessRiScResultDTO
import no.risc.risc.models.ProcessingStatus

data class RepositoryAccessException(
    val response: Any =
        ProcessRiScResultDTO(
            riScId = "",
            status = ProcessingStatus.NoWriteAccessToRepository,
            statusMessage = "Access denied: No write permission",
        ),
    override val message: String?,
) : Exception()
