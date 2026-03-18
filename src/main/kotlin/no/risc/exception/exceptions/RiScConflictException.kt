package no.risc.exception.exceptions

class RiScConflictException(
    override val message: String,
    val riScId: String,
    cause: Throwable? = null,
) : Exception(message, cause)
