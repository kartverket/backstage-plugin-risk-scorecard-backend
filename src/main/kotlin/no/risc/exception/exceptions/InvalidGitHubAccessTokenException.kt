package no.risc.exception.exceptions

class InvalidGithubAccessTokenException (
    override val message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
