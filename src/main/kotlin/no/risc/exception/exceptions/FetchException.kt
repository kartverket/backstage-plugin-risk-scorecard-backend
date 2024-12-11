package no.risc.exception.exceptions

import no.risc.risc.ProcessingStatus

data class FetchException(
    override val message: String,
    val status: ProcessingStatus,
) : Exception()
