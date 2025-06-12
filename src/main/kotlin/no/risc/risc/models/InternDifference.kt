package no.risc.risc.models

import no.risc.utils.comparison.RiScChange

class InternDifference(
    val status: DifferenceStatus,
    val differenceState: RiScChange? = null,
    val errorMessage: String = "",
) {
    fun toDTO(date: String = ""): DifferenceDTO =
        DifferenceDTO(
            status = status,
            differenceState = differenceState,
            errorMessage = errorMessage,
            defaultLastModifiedDateString = date,
        )
}
