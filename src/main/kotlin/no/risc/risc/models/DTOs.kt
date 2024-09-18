package no.risc.risc.models

import no.risc.risc.DifferenceStatus
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
