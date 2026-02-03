package no.risc.google.model

import kotlinx.serialization.Serializable

@Serializable
enum class CryptoKeyPermission {
    DECRYPT,
    ENCRYPT,
}
