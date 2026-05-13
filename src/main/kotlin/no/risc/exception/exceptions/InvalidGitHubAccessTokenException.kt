package no.risc.exception.exceptions

class InvalidGitHubAccessTokenException(
    override val message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
