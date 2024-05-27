package no.risc.exception.exceptions

data class SopsEncryptionException(
    override val message: String,
    val riScId: String,
) : Exception()
