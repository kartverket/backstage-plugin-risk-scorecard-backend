package no.risc.utils

import no.risc.sops.model.SopsConfigRequestBody
import no.risc.sops.model.getValidationResult

data class ValidationResult(
    val isValid: Boolean,
    val message: String = "",
)

object Validator {
    fun validate(body: SopsConfigRequestBody): ValidationResult {
        val gcpProjectIdValidationResult = body.gcpProjectId.getValidationResult()
        val publicKeysValidationResult = body.publicAgeKeys.map { it.getValidationResult() }
        return if (publicKeysValidationResult.any { !it.isValid }) {
            publicKeysValidationResult.first { !it.isValid }
        } else {
            gcpProjectIdValidationResult
        }
    }
}
