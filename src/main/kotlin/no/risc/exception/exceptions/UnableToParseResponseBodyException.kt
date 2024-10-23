package no.risc.exception.exceptions

data class UnableToParseResponseBodyException(
    override val message: String,
) : Exception()
