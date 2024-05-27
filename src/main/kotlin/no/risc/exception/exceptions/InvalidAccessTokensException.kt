package no.risc.exception.exceptions

data class InvalidAccessTokensException(
    override val message: String?
) : Exception()
