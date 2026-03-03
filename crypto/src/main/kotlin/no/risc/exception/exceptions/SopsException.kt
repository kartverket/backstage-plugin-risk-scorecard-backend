package no.risc.exception.exceptions

class SopsEncryptionException(
    override val message: String,
    val riScId: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class SOPSDecryptionException(
    override val message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
