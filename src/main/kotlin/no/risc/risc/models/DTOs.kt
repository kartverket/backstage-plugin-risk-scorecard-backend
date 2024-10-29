package no.risc.risc.models

import no.risc.risc.DifferenceStatus
import no.risc.risc.ProcessingStatus
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

data class InitializeRiScRequestBody(
    val publicAgeKey: String? = null,
    val gcpProjectId: String,
)

data class ScheduleInitialRiScDTO(
    val status: ProcessingStatus,
    val statusMessage: String,
)

enum class ScheduleInitialRiScStatus {
    Success,
    Failure,
    NotImplemented,
}
