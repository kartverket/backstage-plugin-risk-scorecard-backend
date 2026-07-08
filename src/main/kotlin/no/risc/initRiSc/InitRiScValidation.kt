package no.risc.initRiSc

import no.risc.validation.ValidationResult

private val INIT_RISC_ID_PATTERN = Regex("^[A-Za-z0-9_-]+$")

fun validateInitRiScId(id: String): ValidationResult =
    if (INIT_RISC_ID_PATTERN.matches(id)) {
        ValidationResult(true)
    } else {
        ValidationResult(false, "Invalid init-RiSc id: '$id'")
    }
