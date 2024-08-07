package no.risc

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.risc.risc.ContentStatus
import no.risc.risc.RiScContentResultDTO
import no.risc.risc.RiScStatus
import no.risc.utils.migrate
import no.risc.utils.migrateFrom33To40
import no.risc.utils.migrateTo32To33
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.io.File

class MigrationFunctionTests {
    private val latestSupportedVersion = "4.0"

    @Test
    fun `test migrateFrom33To32`() {
        val resourcePath = "3.2.json"
        val resourceUrl = object {}.javaClass.classLoader.getResource(resourcePath)
        val file = File(resourceUrl!!.toURI())
        val fileContent = file.readText()
        val obj =
            RiScContentResultDTO(
                riScId = "1",
                status = ContentStatus.Success,
                riScContent = fileContent,
                riScStatus = RiScStatus.Published,
                migrationChanges = false,
            )
        val migratedObject = migrateTo32To33(obj)

        val json = Json { ignoreUnknownKeys = true }
        val migratedJsonObject = json.parseToJsonElement(migratedObject.riScContent!!).jsonObject

        val schemaVersion = migratedJsonObject["schemaVersion"]?.jsonPrimitive?.content

        assertEquals("3.3", schemaVersion)
    }

    @Test
    fun `test migrateFrom33To40`() {
        val resourcePath = "3.3.json"
        val resourceUrl = object {}.javaClass.classLoader.getResource(resourcePath)

        val file = File(resourceUrl!!.toURI())
        val fileContent = file.readText()
        val obj =
            RiScContentResultDTO(
                riScId = "2",
                status = ContentStatus.Success,
                riScContent = fileContent,
                riScStatus = RiScStatus.Published,
                migrationChanges = false,
            )
        val migratedObject = migrateFrom33To40(obj)

        val json = Json { ignoreUnknownKeys = true }
        val migratedJsonObject = json.parseToJsonElement(migratedObject.riScContent!!).jsonObject.toMap()

        val schemaVersion = migratedJsonObject["schemaVersion"]?.jsonPrimitive?.content
        assertEquals("4.0", schemaVersion)

        // Check that existingActions has been removed
        val scenarios = migratedJsonObject["scenarios"]?.jsonArray
        scenarios?.forEach { scenario ->
            val scenarioObject = scenario.jsonObject
            val scenarioDetails = scenarioObject["scenario"]?.jsonObject

            scenarioDetails?.let {
                assertFalse(it.containsKey("existingActions"))

                val vulnerabilitiesArray =
                    it["vulnerabilities"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()

                val expectedVulnerabilities =
                    listOf(
                        "Unmonitored use",
                        "Unauthorized access",
                        "Information leak",
                        "Excessive use",
                    )
                assertEquals(expectedVulnerabilities, vulnerabilitiesArray)

                val actionsArray = it["actions"]?.jsonArray
                actionsArray?.forEach { action ->
                    val actionObject = action.jsonObject["action"]?.jsonObject
                    actionObject?.let { ao ->
                        assertFalse(ao.containsKey("owner"))
                        assertFalse(ao.containsKey("deadline"))
                    }
                }
            }

            assertEquals(true, migratedObject.migrationChanges)
        }
    }

    @Test
    fun testMigrate() {
        val resourcePath = "3.2.json"
        val resourceUrl = object {}.javaClass.classLoader.getResource(resourcePath)

        val file = File(resourceUrl!!.toURI())
        val fileContent = file.readText()
        val obj =
            RiScContentResultDTO(
                riScId = "3",
                status = ContentStatus.Success,
                riScContent = fileContent,
                riScStatus = RiScStatus.Published,
                migrationChanges = false,
            )
        val migratedObject = migrate(obj, latestSupportedVersion)

        val json = Json { ignoreUnknownKeys = true }
        val migratedJsonObject = json.parseToJsonElement(migratedObject.riScContent!!).jsonObject.toMap()

        val schemaVersion = migratedJsonObject["schemaVersion"]?.jsonPrimitive?.content
        assertEquals(latestSupportedVersion, schemaVersion)
    }
}
