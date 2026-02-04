package no.risc.exception.exceptions

import no.risc.risc.models.ProcessingStatus

data class FetchException(
    override val message: String,
    val status: ProcessingStatus,
) : Exception()
