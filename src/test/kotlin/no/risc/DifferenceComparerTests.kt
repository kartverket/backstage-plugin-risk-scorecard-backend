package no.risc

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import no.risc.utils.DifferenceException
import no.risc.utils.FlatMapRiScUtil
import no.risc.utils.diff
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrowsExactly
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DifferenceComparerTests {
    @Suppress("ktlint:standard:max-line-length")
    private var baseContent = "{\"schemaVersion\":\"3.8\", \"title\":\"Versjon 4 \",\"scope\":\"Finne ut av siste versjon\",\"valuations\":[],\"scenarios\":[{\"title\":\"Rekkefølge\",\"scenario\":{\"ID\":\"h4i9Y\",\"description\":\"hei\",\"threatActors\":[],\"vulnerabilities\":[],\"risk\":{\"summary\":\"\",\"probability\":0.01,\"consequence\":1000},\"actions\":[],\"remainingRisk\":{\"summary\":\"\",\"probability\":0.01,\"consequence\":1000}}},{\"title\":\"Testing 4.0\",\"scenario\":{\"ID\":\"rFww8\",\"description\":\"sfd\",\"threatActors\":[\"Organised crime\"],\"vulnerabilities\":[\"Flawed design\",\"Unauthorized access\",\"Unmonitored use\",\"Excessive use\"],\"risk\":{\"summary\":\"\",\"probability\":1,\"consequence\":1000},\"actions\":[{\"title\":\"\",\"action\":{\"ID\":\"27A7C\",\"description\":\"Går dette?\",\"status\":\"Not started\",\"url\":\"www.nrk.no\"}}],\"remainingRisk\":{\"summary\":\"\",\"probability\":0.01,\"consequence\":1000}}}]}"

    @Suppress("ktlint:standard:max-line-length")
    private var headContent = "{\"schemaVersion\":\"4.0\",\"title\":\"Versjon 4 \",\"valuations\":[],\"scenarios\":[{\"title\":\"Rekkefølge\",\"scenario\":{\"ID\":\"h4i9Y\",\"description\":\"\",\"vulnerabilities\":[\"Skummel type\"],\"risk\":{\"summary\":\"\",\"probability\":0.01,\"consequence\":1000},\"actions\":[],\"remainingRisk\":{\"summary\":\"\",\"probability\":0.01,\"consequence\":1000}}},{\"title\":\"Testing 4.0\",\"scenario\":{\"ID\":\"rFww8\",\"description\":\"sfd\",\"threatActors\":[\"Organised crime\"],\"vulnerabilities\":[\"Flawed design\",\"Unauthorized access\",\"Unmonitored use\",\"Excessive use\"],\"risk\":{\"summary\":\"\",\"probability\":1,\"consequence\":1000},\"actions\":[{\"title\":\"\",\"action\":{\"ID\":\"27A7C\",\"description\":\"Går dette?\",\"status\":\"Not started\",\"url\":\"www.nrk.no\"}}],\"remainingRisk\":{\"summary\":\"\",\"probability\":0.01,\"consequence\":1000}}}]}"

    @Test
    fun `when two normal riscs with minor differences is checked then it shows deletions, additions, and changes `() {
        // Act
        val result = diff(baseContent, headContent)

        // Assert
        assertEquals(3, result.entriesOnLeft.size)
        assertEquals(2, result.difference.size)
        assertEquals(1, result.entriesOnRight.size)
    }

    @Test
    fun expectExceptionOnWrongInput() {
        // Arrange
        val corruptContent = "[\"not actually a JsonObject\"]"

        // Act & Assert
        assertThrowsExactly(DifferenceException::class.java) { diff(baseContent, corruptContent) }
    }

    @Test
    fun `test flatten JSON`() {
        val json =
            "{ \"firstKey\": \"hello\", \"secondKey\": { \"thirdKey\": [ \"world\", \"and\", \"others\"], \"fourthKey\": 4 } }"
        val jsonObject = Json.parseToJsonElement(json).jsonObject

        val flattened = FlatMapRiScUtil.flatten(jsonObject)

        assertEquals(flattened.size, flattened.distinct().size, "No element should be repeated in the flattened JSON.")

        val expectedValues =
            listOf(
                "/firstKey: \"hello\"",
                "/secondKey/thirdKey/0: \"world\"",
                "/secondKey/thirdKey/1: \"and\"",
                "/secondKey/thirdKey/2: \"others\"",
                "/secondKey/fourthKey: 4",
            )

        assertEquals(expectedValues.size, flattened.size, "The flattened JSON contains too few/many items")
        assertTrue(flattened.toSet().containsAll(expectedValues), "The flattened JSON does not contain all the expected items")
    }

    @Test
    fun `test flatten JSON handles unknown types`() {
        val obj = Object()
        val map = mapOf("object" to obj)
        val flattened = FlatMapRiScUtil.flatten(map)

        assertEquals(1, flattened.size)
        assertTrue(flattened.contains("/object - $obj is Unknown"))
    }
}
