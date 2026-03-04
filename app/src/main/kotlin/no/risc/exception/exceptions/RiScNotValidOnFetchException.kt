package no.risc.exception.exceptions

data class RiScNotValidOnFetchException(
    override val message: String,
    val riScId: String,
) : Exception()
