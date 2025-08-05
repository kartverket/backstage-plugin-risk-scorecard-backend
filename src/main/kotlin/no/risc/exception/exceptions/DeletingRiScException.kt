package no.risc.exception.exceptions

data class DeletingRiScException(
    override val message: String,
    val riScId: String,
) : Exception()
