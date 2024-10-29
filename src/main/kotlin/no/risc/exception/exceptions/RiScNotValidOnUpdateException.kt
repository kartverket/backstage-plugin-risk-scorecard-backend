package no.risc.exception.exceptions

data class RiScNotValidOnUpdateException(
    override val message: String,
    val riScId: String,
    val validationError: String,
) : Exception()
