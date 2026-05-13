package no.risc.exception.exceptions

class InvalidAccessTokensException(
    override val message: String?,
    cause: Throwable? = null,
) : Exception(message, cause)
