package no.risc.exception.exceptions

data class SopsConfigFetchException(
    override val message: String,
    val riScId: String,
) : Exception()
