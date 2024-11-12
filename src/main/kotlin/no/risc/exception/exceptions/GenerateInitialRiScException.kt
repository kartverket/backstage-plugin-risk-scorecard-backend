package no.risc.exception.exceptions

data class GenerateInitialRiScException(
    override val message: String,
    val riScId: String,
) : Exception()
