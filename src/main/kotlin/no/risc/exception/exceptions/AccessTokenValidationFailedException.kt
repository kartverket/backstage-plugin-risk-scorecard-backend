package no.risc.exception.exceptions

class AccessTokenValidationFailedException(
    override val message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
