package no.risc.google.model

data class GcpCryptoKeyObject(
    val projectId: String,
    val keyRing: String,
    val name: String,
    val resourceId: String,
    val hasEncryptDecryptAccess: Boolean,
)
