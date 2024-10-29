package no.risc.exception.exceptions

import no.risc.risc.ProcessingStatus
import no.risc.risc.models.ScheduleInitialRiScDTO

data class ScheduleInitialRiScDuringLocalException(
    override val message: String,
    val response: ScheduleInitialRiScDTO =
        ScheduleInitialRiScDTO(
            ProcessingStatus.ErrorWhenSchedulingInitialRiSc,
            "Schedule initial RiSc is not supported during local run",
        ),
) : Exception()
