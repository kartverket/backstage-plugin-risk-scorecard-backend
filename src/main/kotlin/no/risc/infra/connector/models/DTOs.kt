package no.risc.infra.connector.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class FetchGcpProjectIdsResponse(
    val projects: List<GcpProject>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GcpProject(
    val projectId: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class FetchCryptoKeysResponse(
    val cryptoKeys: List<GcpCryptoKey>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GcpCryptoKey(
    @JsonProperty("name") val resourceId: String,
    val primary: GcpCryptoKeyPrimaryInformation? = null,
    val purpose: GcpCryptoKeyPurpose,
) {
    fun getCryptoKeyName() = resourceId.split("/").last()
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class GcpCryptoKeyPrimaryInformation(
    val state: GcpCryptoKeyPrimaryState,
)

enum class GcpCryptoKeyPrimaryState {
    CRYPTO_KEY_VERSION_STATE_UNSPECIFIED,
    PENDING_GENERATION,
    ENABLED,
    DISABLED,
    DESTROYED,
    DESTROY_SCHEDULED,
    PENDING_IMPORT,
    IMPORT_FAILED,
    GENERATION_FAILED,
    PENDING_EXTERNAL_DESTRUCTION,
    EXTERNAL_DESTRUCTION_FAILED,
}

enum class GcpCryptoKeyPurpose {
    CRYPTO_KEY_PURPOSE_UNSPECIFIED,
    ENCRYPT_DECRYPT,
    ASYMMETRIC_SIGN,
    ASYMMETRIC_DECRYPT,
    RAW_ENCRYPT_DECRYPT,
    MAC,
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class TestIamPermissionBody(
    val permissions: List<String>,
)
