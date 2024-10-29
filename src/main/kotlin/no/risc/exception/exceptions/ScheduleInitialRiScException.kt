package no.risc.exception.exceptions

import no.risc.risc.ProcessingStatus
import no.risc.risc.models.ScheduleInitialRiScDTO

data class ScheduleInitialRiScException(
    override val message: String,
    val response: ScheduleInitialRiScDTO =
        ScheduleInitialRiScDTO(
            ProcessingStatus.ErrorWhenSchedulingInitialRiSc,
            "Failed to schedule initial RiSc generation",
        ),
) : Exception()
