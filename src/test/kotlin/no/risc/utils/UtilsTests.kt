package no.risc.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class UtilsTests {
    @Test
    fun `test generate random alphanumeric string`() {
        val alphaNumericChars: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        // Fix random for reproducible tests
        val random = Random(1)

        val randomString = generateRandomAlphanumericString(length = 300, random = random)
        assertEquals(300, randomString.length, "Generated string should have the requested length")
        assertTrue(
            randomString.toCharArray().all { alphaNumericChars.contains(it) },
            "The generated string should only contain alpha numeric characters",
        )
    }
}
