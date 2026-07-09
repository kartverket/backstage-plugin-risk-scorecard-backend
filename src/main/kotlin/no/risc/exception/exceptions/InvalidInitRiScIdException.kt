package no.risc.exception.exceptions

class InvalidInitRiScIdException(
    override val message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
