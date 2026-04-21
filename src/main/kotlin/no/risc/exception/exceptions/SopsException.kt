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
    val encryptionKeyId: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause) {
    companion object {
        fun errorMessageFromCode(errorCode: String): String =
            when (errorCode) {
                "MISSING_DATA_KEY" -> "Failed to get the data key required for decryption"
                "NO_MATCHING_KEY" -> "No matching key found for decryption"
                "AUTHENTICATION_FAILED" -> "Authentication failed when accessing the encryption key"
                else -> "Decryption failed"
            }
    }

    fun rootCauseMessage(): String? {
        var root: Throwable? = this
        while (root?.cause != null && root.cause !== root) {
            root = root.cause
        }
        return root?.message
    }
}
