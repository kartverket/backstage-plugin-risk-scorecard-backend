package no.risc.utils

import java.security.MessageDigest

object Hasher {
    private fun hash(
        input: Any,
        algorithm: String
    ): String {
        val bytes = input.toString().toByteArray()
        val md = MessageDigest.getInstance(algorithm)
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }

    fun String.sha256() = hash(
        input = this,
        algorithm = "SHA-256",
    )
}