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
