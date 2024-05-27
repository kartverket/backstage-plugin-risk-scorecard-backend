package no.risc.exception.exceptions

data class UpdatingRiScException (
    override val message: String,
    val riScId: String,
) : Exception()