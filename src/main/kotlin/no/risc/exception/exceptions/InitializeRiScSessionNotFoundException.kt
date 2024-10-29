package no.risc.exception.exceptions

data class InitializeRiScSessionNotFoundException(
    override val message: String,
) : Exception()
