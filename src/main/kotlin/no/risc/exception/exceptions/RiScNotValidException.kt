package no.risc.exception.exceptions

data class RiScNotValidException(
    override val message: String,
    val riScId: String,
    val validationError: String,
) : Exception()
