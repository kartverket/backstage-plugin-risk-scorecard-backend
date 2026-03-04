package no.risc.exception.exceptions

class SopsEncryptionException(
    override val message: String,
    val riScId: String,
    val errorCode: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause)

class SOPSDecryptionException(
    override val message: String,
    val errorCode: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause)
