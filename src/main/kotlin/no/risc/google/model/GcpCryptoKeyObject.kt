package no.risc.google.model

import kotlinx.serialization.Serializable

@Serializable
data class GcpCryptoKeyObject(
    val projectId: String,
    val keyRing: String,
    val name: String,
    val resourceId: String,
    val hasEncryptDecryptAccess: Boolean,
)
