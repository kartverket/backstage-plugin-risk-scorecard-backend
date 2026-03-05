package no.risc.exception.exceptions

class SopsEncryptionException(
    override val message: String,
    val riScId: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class SOPSDecryptionException(
    override val message: String,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause) {
    fun rootCauseMessage(): String? {
        var root: Throwable? = this
        while (root?.cause != null && root.cause !== root) {
            root = root.cause
        }
        return root?.message
    }
}
