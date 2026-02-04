package no.risc.exception.exceptions

import no.risc.risc.models.ProcessingStatus

class FetchException(
    override val message: String,
    val status: ProcessingStatus,
    cause: Throwable? = null,
) : Exception(message, cause)
