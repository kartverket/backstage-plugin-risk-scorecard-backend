package no.risc.exception.exceptions

data class RosaEncryptionException(
    override val message: String,
) : Exception()
