package no.risc.exception.exceptions

data class NoWriteAccessToRepositoryException(
    val response: Any,
    override val message: String,
) : Exception()
