package no.risc.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
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

    @Test
    fun `generateRiScIdFromBackstageInfo returns full backstage format when all fields are present`() {
        val result =
            generateRiScIdFromBackstageInfo(
                filenamePrefix = "risc",
                riscName = "my-risc",
                backstageKind = "component",
                backstageNamespace = "my-namespace",
                backstageName = "my-service",
            )
        assertEquals("risc-my-risc-backstage_component_my-namespace_my-service", result)
    }

    @Test
    fun `generateRiScIdFromBackstageInfo uses default namespace when backstageNamespace is null`() {
        val result =
            generateRiScIdFromBackstageInfo(
                filenamePrefix = "risc",
                riscName = "my-risc",
                backstageKind = "component",
                backstageNamespace = null,
                backstageName = "my-service",
            )
        assertEquals("risc-my-risc-backstage_component_default_my-service", result)
    }

    @Test
    fun `generateRiScIdFromBackstageInfo returns simple format when only riscName is provided`() {
        val result =
            generateRiScIdFromBackstageInfo(
                filenamePrefix = "risc",
                riscName = "my-risc",
                backstageKind = null,
                backstageNamespace = null,
                backstageName = null,
            )
        assertEquals("risc-my-risc", result)
    }

    @Test
    fun `generateRiScIdFromBackstageInfo returns null when riscName is null`() {
        val result =
            generateRiScIdFromBackstageInfo(
                filenamePrefix = "risc",
                riscName = null,
                backstageKind = null,
                backstageNamespace = null,
                backstageName = null,
            )
        assertNull(result)
    }

    @Test
    fun `generateRiScIdFromBackstageInfo returns null when backstageKind is null but backstageName is present`() {
        val result =
            generateRiScIdFromBackstageInfo(
                filenamePrefix = "risc",
                riscName = "my-risc",
                backstageKind = null,
                backstageNamespace = null,
                backstageName = "my-service",
            )
        assertNull(result)
    }

    @Test
    fun `generateRiScIdFromBackstageInfo returns null when backstageName is null but backstageKind is present`() {
        val result =
            generateRiScIdFromBackstageInfo(
                filenamePrefix = "risc",
                riscName = "my-risc",
                backstageKind = "component",
                backstageNamespace = null,
                backstageName = null,
            )
        assertNull(result)
    }

    @Test
    fun `riScIdMatchesBackstageFilter returns true when no filter is set`() {
        assertTrue(
            riScIdMatchesBackstageFilter(
                riScId = "risc-abc12",
                backstageKind = null,
                backstageNamespace = null,
                backstageName = null,
            ),
        )
    }

    @Test
    fun `riScIdMatchesBackstageFilter returns true for old 5-chars format when filter is set`() {
        assertTrue(
            riScIdMatchesBackstageFilter(
                riScId = "risc-abc12",
                backstageKind = "service",
                backstageNamespace = null,
                backstageName = "x",
            ),
        )
    }

    @Test
    fun `riScIdMatchesBackstageFilter returns true for old name format without backstage segment`() {
        assertTrue(
            riScIdMatchesBackstageFilter(
                riScId = "risc-my-risc",
                backstageKind = "service",
                backstageNamespace = null,
                backstageName = "x",
            ),
        )
    }

    @Test
    fun `riScIdMatchesBackstageFilter returns true for matching backstage ID with null namespace defaulting to default`() {
        assertTrue(
            riScIdMatchesBackstageFilter(
                riScId = "risc-a-backstage_service_default_x",
                backstageKind = "service",
                backstageNamespace = null,
                backstageName = "x",
            ),
        )
    }

    @Test
    fun `riScIdMatchesBackstageFilter returns true for matching backstage ID with explicit namespace`() {
        assertTrue(
            riScIdMatchesBackstageFilter(
                riScId = "risc-a-backstage_service_my-ns_x",
                backstageKind = "service",
                backstageNamespace = "my-ns",
                backstageName = "x",
            ),
        )
    }

    @Test
    fun `riScIdMatchesBackstageFilter returns false when entity name does not match`() {
        assertFalse(
            riScIdMatchesBackstageFilter(
                riScId = "risc-a-backstage_service_default_x",
                backstageKind = "service",
                backstageNamespace = null,
                backstageName = "other",
            ),
        )
    }

    @Test
    fun `riScIdMatchesBackstageFilter returns false when kind does not match`() {
        assertFalse(
            riScIdMatchesBackstageFilter(
                riScId = "risc-a-backstage_service_default_x",
                backstageKind = "api",
                backstageNamespace = null,
                backstageName = "x",
            ),
        )
    }

    @Test
    fun `riScIdMatchesBackstageFilter returns false when namespace does not match`() {
        assertFalse(
            riScIdMatchesBackstageFilter(
                riScId = "risc-a-backstage_service_other-ns_x",
                backstageKind = "service",
                backstageNamespace = "default",
                backstageName = "x",
            ),
        )
    }
}
