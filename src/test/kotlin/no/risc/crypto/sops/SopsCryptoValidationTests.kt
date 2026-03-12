package no.risc.crypto.sops

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class SopsCryptoValidationTests {
    private val validAgeSecretKey = "AGE-SECRET-KEY-1JXMRGWL3HYZXZPYF98YHEF0AHZR8J8J58YWAYUS8448Q9QE0AW2S3KK5GT"

    @Test
    fun `when the gcp token has valid format validation succeeds`() {
        val validToken =
            "ya29.a0AeXRPp49V58XuoU6xfdO2qWndhdnExfAt97odE9Crs5PWgXwzc9TN2xbaQyxAsY8tD" +
                "hRva8TPMGcMPlhCw21NC3nXMR-ROa-TW5VQT6z2bJZD1VDDZJmBQpbMg0_ZISAwB7" +
                "qiW97eGRM4Pt2nqRkMJKFxAffieFYG8bJmrPkyeO8bgaCgYKAUgSARISFQHGX2MiC" +
                "vLSwEAjMDXyiIWIkWSiHA0181"

        assertTrue(SopsCryptoValidation.isValidGCPToken(validToken))
    }

    @Test
    fun `when the gcp token has padding character at the end validation succeeds`() {
        assertTrue(SopsCryptoValidation.isValidGCPToken("a9B8=="))
    }

    @Test
    fun `when the gcp token has padding character inside validation fails`() {
        assertFalse(SopsCryptoValidation.isValidGCPToken("a9=B8"))
    }

    @ParameterizedTest
    @ValueSource(strings = arrayOf(" ", ",", "|", "\"", "'", "(", ")", "[", "]", "{", "}")) // six numbers
    fun `when the gcp token has invalid characters validation fails`(char: String) {
        assertFalse(SopsCryptoValidation.isValidGCPToken("a9" + char + "B8"))
    }

    @ParameterizedTest
    @ValueSource(strings = arrayOf(".", "-", "_", "~", "+", "/")) // six numbers
    fun `when the gcp token has valid characters validation succeeds`(char: String) {
        assertTrue(SopsCryptoValidation.isValidGCPToken("a9" + char + "B8"))
    }

    @Test
    fun `when the age secret key is valid validation succeeds`() {
        assertTrue(SopsCryptoValidation.isValidAgeSecretKey(validAgeSecretKey))
    }

    @Test
    fun `when the age secret key is off by a character validation fails`() {
        val invalidAgeSecretKey = validAgeSecretKey.replaceFirst('J', 'K')
        assertFalse(SopsCryptoValidation.isValidAgeSecretKey(invalidAgeSecretKey))
    }
}
