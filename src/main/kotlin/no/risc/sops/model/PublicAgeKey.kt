package no.risc.sops.model

import no.risc.utils.ValidationResult

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
