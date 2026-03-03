package no.risc.validation

import no.risc.google.model.GcpCryptoKeyObject
import no.risc.google.model.GcpProjectId
import no.risc.google.model.getValidationResult

data class ValidationResult(
    val isValid: Boolean,
    val message: String = "",
)

/**
 * Determines whether the project ID is in a valid format.
 */
fun GcpCryptoKeyObject.getValidationResult() = GcpProjectId(projectId).getValidationResult()

@JvmInline
value class PublicAgeKey(
    val value: String,
)

fun PublicAgeKey.getValidationResult(): ValidationResult {
    if (value.length != 62) {
        return ValidationResult(false, "Public age key: '$value' is not 62 characters long")
    }

    if (!value.startsWith("age1")) {
        return ValidationResult(false, "Public age key: '$value' does not start with 'age1'")
    }

    return if (Regex("^age1[ac-hj-np-z02-9]{58}$").matches(value)) {
        ValidationResult(true)
    } else {
        ValidationResult(false, "The part after 'age1' in '$value' is not base58-formatted")
    }
}
