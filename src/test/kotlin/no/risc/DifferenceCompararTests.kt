package no.risc

import kotlinx.serialization.json.*
import no.risc.utils.FlatMapUtils3
import no.risc.utils.diff
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DifferenceCompararTests {

    private var content = "{\"schemaVersion\":\"4.0\", \"myObject\":{}, \"myList\": [], \"title\":\"Versjon 4 \",\"scope\":\"Finne ut av siste versjon\",\"valuations\":[],\"scenarios\":[{\"title\":\"Rekkefølge\",\"scenario\":{\"ID\":\"h4i9Y\",\"description\":\"hei\",\"threatActors\":[],\"vulnerabilities\":[],\"risk\":{\"summary\":\"\",\"probability\":0.01,\"consequence\":1000},\"actions\":[],\"remainingRisk\":{\"summary\":\"\",\"probability\":0.01,\"consequence\":1000}}},{\"title\":\"Testing 4.0\",\"scenario\":{\"ID\":\"rFww8\",\"description\":\"sfd\",\"threatActors\":[\"Organised crime\"],\"vulnerabilities\":[\"Flawed design\",\"Unauthorized access\",\"Unmonitored use\",\"Excessive use\"],\"risk\":{\"summary\":\"\",\"probability\":1,\"consequence\":1000},\"actions\":[{\"title\":\"\",\"action\":{\"ID\":\"27A7C\",\"description\":\"Går dette?\",\"status\":\"Not started\",\"url\":\"www.nrk.no\"}}],\"remainingRisk\":{\"summary\":\"\",\"probability\":0.01,\"consequence\":1000}}}]}"
    private var otherContent = "{\"schemaVersion\":\"3.8\",\"title\":\"Versjon 4 \",\"scope\":\"Finne ut av siste versjon\",\"valuations\":[],\"scenarios\":[{\"title\":\"Rekkefølge\",\"scenario\":{\"ID\":\"h4i9Y\",\"description\":\"\",\"vulnerabilities\":[\"Skummel type\"],\"risk\":{\"summary\":\"\",\"probability\":0.01,\"consequence\":1000},\"actions\":[],\"remainingRisk\":{\"summary\":\"\",\"probability\":0.01,\"consequence\":1000}}},{\"title\":\"Testing 4.0\",\"scenario\":{\"ID\":\"rFww8\",\"description\":\"sfd\",\"threatActors\":[\"Organised crime\"],\"vulnerabilities\":[\"Flawed design\",\"Unauthorized access\",\"Unmonitored use\",\"Excessive use\"],\"risk\":{\"summary\":\"\",\"probability\":1,\"consequence\":1000},\"actions\":[{\"title\":\"\",\"action\":{\"ID\":\"27A7C\",\"description\":\"Går dette?\",\"status\":\"Not started\",\"url\":\"www.nrk.no\"}}],\"remainingRisk\":{\"summary\":\"\",\"probability\":0.01,\"consequence\":1000}}}]}"



    @Test
    fun givesStringOutput() {

        // Act
        val result = diff(content, otherContent)

        println()
        println("Innhold for er fjernet")
        result.entriesOnLeft.forEach { println(it) }
        println()
        println("Innhold for er endret")
        result.difference.forEach { println(it) }
        println()
        println("Innhold som er lagt til")
        result.entriesOnRight.forEach { println(it) }
        println()


        // Assert
        assertEquals(2, 2)
    }



    @Test
    fun workspace() {
        val json = Json { ignoreUnknownKeys = true }
        val jsonObject1 = json.parseToJsonElement(content).jsonObject.toMap()

        val result = FlatMapUtils3.typeChecker(jsonObject1)


        result.forEach { println(it) }

        assertEquals(true, true)
    }

    @Test
    fun jsonParsing() {
        val json = Json { ignoreUnknownKeys = true }
        val jsonObject = json.parseToJsonElement(content).jsonObject.toMap()

        val scenarios = jsonObject.get("scenarios")

        val list = when (scenarios) {
            is List<*> -> json.parseToJsonElement(scenarios.toString())
            else -> null
        }

        if (list != null && list is JsonArray) {
            list.forEach{ println(it) }
        }
    }
}