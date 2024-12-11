package no.risc.sops.model

import no.risc.utils.ValidationResult

@JvmInline
value class GcpProjectId(
    val value: String = "",
)

fun GcpProjectId.getValidationResult() =
    if (Regex("^(\\w+-)+\\w+$").matches(value)) {
        ValidationResult(true)
    } else {
        ValidationResult(false, "Invalid format of GCP Project ID: '$value'")
    }

fun GcpProjectId.getRiScKeyRing() = "${value.split("-").dropLast(2).joinToString("-")}-risc-key-ring"

fun GcpProjectId.getRiScCryptoKey() = "${value.split("-").dropLast(2).joinToString("-")}-risc-crypto-key"

fun GcpProjectId.getRiScCryptoKeyResourceId() =
    "projects/$value/locations/europe-north1/keyRings/${getRiScKeyRing()}/cryptoKeys/${getRiScCryptoKey()}"
