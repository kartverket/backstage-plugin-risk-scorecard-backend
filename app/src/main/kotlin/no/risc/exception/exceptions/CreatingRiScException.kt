package no.risc.exception.exceptions

data class CreatingRiScException(
    override val message: String,
    val riScId: String,
) : Exception()
