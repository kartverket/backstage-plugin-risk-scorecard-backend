package no.risc.exception.exceptions

class InvalidGcpAccessTokenException(
    override val message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
