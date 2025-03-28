package no.risc.validation

import no.risc.sops.model.GcpCryptoKeyObject
import no.risc.sops.model.GcpProjectId
import no.risc.sops.model.SopsConfigRequestBody
import no.risc.sops.model.getValidationResult

data class ValidationResult(
    val isValid: Boolean,
    val message: String = "",
)

/**
 * Determines whether the request body contains at least one age key or GCP key in a valid format.
 */
fun SopsConfigRequestBody.getValidationResult() =
    publicAgeKeys.map { it.getValidationResult() }.firstOrNull { !it.isValid } ?: gcpCryptoKey.getValidationResult()

/**
 * Determines whether the project ID is in a valid format.
 */
fun GcpCryptoKeyObject.getValidationResult() = GcpProjectId(projectId).getValidationResult()
