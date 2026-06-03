package no.risc.exception.exceptions

class SystemRiScInPublicRepositoryException(
    override val message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
