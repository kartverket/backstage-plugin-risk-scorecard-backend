package no.risc.exception.exceptions

data class RiScConflictException(
    override val message: String,
    val riScId: String,
) : Exception()
