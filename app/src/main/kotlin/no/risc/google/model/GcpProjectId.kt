package no.risc.google.model

import no.risc.validation.ValidationResult

data class GcpProjectId(
    val value: String = "",
)

fun GcpProjectId.getValidationResult() =
    if (Regex("^(\\w+-)+\\w+$").matches(value)) {
        ValidationResult(true)
    } else {
        ValidationResult(false, "Invalid format of GCP Project ID: '$value'")
    }

/**
 * Constructs a key ring name specific to the RISC system based on the GCP project ID.
 *
 * The key ring name is derived by removing the last two hyphen-separated segments from the GCP project ID
 * and appending "-risc-key-ring" to the remaining segments.
 *
 * So, for example, if the project ID is spire-ros-5lmr, the return value would be spire-risc-key-ring (removing ros-5lmr)
 *
 * @receiver The `GcpProjectId` instance containing the project ID used for constructing the key ring name.
 * @return A string representing the constructed RISC key ring name.
 */
fun GcpProjectId.getRiScKeyRing() = "${value.split("-").dropLast(2).joinToString("-")}-risc-key-ring"

/**
 * Generates the name of a specific GCP crypto key associated with a given GCP project ID.
 * The crypto key name is derived by truncating the project ID to exclude the last two segments
 * (separated by hyphens) and appending "-risc-crypto-key".
 *
 * So, for example, if the project ID is spire-ros-5lmr, the return value would be spire-risc-crypto-key (removing ros-5lmr)
 *
 * @receiver The GCP project ID from which the crypto key name is generated.
 * @return A string representing the name of the GCP crypto key.
 */
fun GcpProjectId.getRiScCryptoKey() = "${value.split("-").dropLast(2).joinToString("-")}-risc-crypto-key"

fun GcpProjectId.getRiScCryptoKeyResourceId() =
    "projects/$value/locations/europe-north1/keyRings/${getRiScKeyRing()}/cryptoKeys/${getRiScCryptoKey()}"
