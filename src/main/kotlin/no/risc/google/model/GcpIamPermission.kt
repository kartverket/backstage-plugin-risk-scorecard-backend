package no.risc.google.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

enum class GcpIamPermission(
    val value: String,
) {
    USE_TO_ENCRYPT("cloudkms.cryptoKeyVersions.useToEncrypt"),
    USE_TO_DECRYPT("cloudkms.cryptoKeyVersions.useToDecrypt"),
    ;

    companion object {
        @JsonCreator
        fun fromValue(value: String): GcpIamPermission? = GcpIamPermission.entries.firstOrNull { it.value == value }

        val ENCRYPT_DECRYPT = listOf(USE_TO_DECRYPT, USE_TO_ENCRYPT)
    }

    @JsonValue
    fun toValue(): String = value
}
