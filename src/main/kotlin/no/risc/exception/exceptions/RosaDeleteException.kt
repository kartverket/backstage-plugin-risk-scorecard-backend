package no.risc.exception.exceptions

data class RosaDeleteException(
    override val message: String,
    val riScId: String,
) : Exception()
