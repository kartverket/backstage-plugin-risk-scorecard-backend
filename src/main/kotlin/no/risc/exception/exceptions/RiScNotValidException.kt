package no.risc.exception.exceptions

data class RiScNotValidException(
    override val message: String,
    val riScId: String,
    val validationError: String,
) : Exception()

data class RiScCouldNotCreateNewException(
    override val message: String,
    val riScId: String,
) : Exception()

data class RiScCouldNotUpdateException(
    override val message: String,
    val riScId: String,
) : Exception()
