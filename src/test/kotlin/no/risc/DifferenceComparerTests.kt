package no.risc

import no.risc.utils.DifferenceException
import no.risc.utils.diff
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrowsExactly
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
}
