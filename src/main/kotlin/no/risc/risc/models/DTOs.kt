package no.risc.risc.models

import no.risc.risc.DifferenceStatus
import no.risc.risc.ProcessingStatus
import no.risc.risc.RiScContentResultDTO
import no.risc.utils.Difference

data class DifferenceDTO(
    val status: DifferenceStatus,
    val differenceState: Difference,
    val errorMessage: String = "",
    val defaultLastModifiedDateString: String = "",
)

data class DifferenceRequestBody(
    val riSc: String,
)

data class GenerateInitialRiScRequestBody(
    val publicAgeKey: String? = null,
    val gcpProjectId: String,
)

data class GenerateInitialRiScResponse(
    val riSc: RiScContentResultDTO,
    val status: ProcessingStatus,
    val statusMessage: String,
)
