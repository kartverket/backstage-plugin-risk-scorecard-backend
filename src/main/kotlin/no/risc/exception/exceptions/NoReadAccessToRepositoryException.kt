package no.risc.exception.exceptions

data class NoReadAccessToRepositoryException(
    val response: Any,
    override val message: String?,
) : Exception()
