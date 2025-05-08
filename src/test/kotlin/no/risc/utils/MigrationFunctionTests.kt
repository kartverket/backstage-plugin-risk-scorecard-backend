package no.risc.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.risc.risc.ContentStatus
import no.risc.risc.MigrationStatus
import no.risc.risc.MigrationVersions
import no.risc.risc.RiScContentResultDTO
import no.risc.risc.RiScStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.io.File

class MigrationFunctionTests {
    private val latestSupportedVersion = "4.1"

    @Test
    fun `test migrateFrom32To33`() {
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
                migrationStatus =
                    MigrationStatus(
                        migrationChanges = false,
                        migrationRequiresNewApproval = false,
                        migrationVersions = MigrationVersions(fromVersion = null, toVersion = null),
                    ),
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
                migrationStatus =
                    MigrationStatus(
                        migrationChanges = false,
                        migrationRequiresNewApproval = true,
                        migrationVersions = MigrationVersions(fromVersion = null, toVersion = null),
                    ),
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

            assertEquals(true, migratedObject.migrationStatus?.migrationChanges)
            assertEquals(true, migratedObject.migrationStatus?.migrationRequiresNewApproval)
        }
    }

    @Test
    fun `test migrate33To40NoScenarios`() {
        val resourcePath = "3.3-no-scenarios.json"
        val resourceUrl = object {}.javaClass.classLoader.getResource(resourcePath)
        val file = File(resourceUrl!!.toURI())
        val fileContent = file.readText()
        val obj =
            RiScContentResultDTO(
                riScId = "3",
                status = ContentStatus.Success,
                riScContent = fileContent,
                riScStatus = RiScStatus.Draft,
                migrationStatus =
                    MigrationStatus(
                        migrationChanges = false,
                        migrationRequiresNewApproval = true,
                        migrationVersions = MigrationVersions(fromVersion = null, toVersion = null),
                    ),
            )
        val migratedObject = migrateFrom33To40(obj)

        val json = Json { ignoreUnknownKeys = true }
        val migratedJsonObject = json.parseToJsonElement(migratedObject.riScContent!!).jsonObject.toMap()

        val schemaVersion = migratedJsonObject["schemaVersion"]?.jsonPrimitive?.content
        assertEquals("4.0", schemaVersion)
    }

    @Test
    fun `test migrateFrom40To41`() {
        val resourcePath = "4.0.json"
        val resourceUrl = object {}.javaClass.classLoader.getResource(resourcePath)

        val file = File(resourceUrl!!.toURI())
        val fileContent = file.readText()
        val obj =
            RiScContentResultDTO(
                riScId = "2",
                status = ContentStatus.Success,
                riScContent = fileContent,
                riScStatus = RiScStatus.Published,
                migrationStatus =
                    MigrationStatus(
                        migrationChanges = false,
                        migrationRequiresNewApproval = true,
                        migrationVersions = MigrationVersions(fromVersion = null, toVersion = null),
                    ),
            )
        val migratedObject = migrateFrom40To41(obj)

        val json = Json { ignoreUnknownKeys = true }
        val migratedJsonObject = json.parseToJsonElement(migratedObject.riScContent!!).jsonObject.toMap()

        // Check that schema version is set to 4.1
        val schemaVersion = migratedJsonObject["schemaVersion"]?.jsonPrimitive?.content
        assertEquals("4.1", schemaVersion)

        // Check that all consequence and probability values have been correctly migrated
        val scenarios = migratedJsonObject["scenarios"]?.jsonArray
        val firstScenario = scenarios?.get(0)?.jsonObject?.get("scenario")?.jsonObject

        firstScenario?.let {
            val risk = it["risk"]?.jsonObject

            val consequence = risk?.get("consequence")?.jsonPrimitive?.content
            val probability = risk?.get("probability")?.jsonPrimitive?.content

            assertEquals(160_000.toString(), consequence)
            assertEquals(1.toString(), probability)

            val remainingRisk = it["remainingRisk"]?.jsonObject

            val remainingConsequence = remainingRisk?.get("consequence")?.jsonPrimitive?.content
            val remainingProbability = remainingRisk?.get("probability")?.jsonPrimitive?.content

            assertEquals(8_000.toString(), remainingConsequence)
            assertEquals(0.05.toString(), remainingProbability)
        }

        val secondScenario = scenarios?.get(1)?.jsonObject?.get("scenario")?.jsonObject

        secondScenario?.let {
            val risk = it["risk"]?.jsonObject

            val consequence = risk?.get("consequence")?.jsonPrimitive?.content
            val probability = risk?.get("probability")?.jsonPrimitive?.content

            assertEquals(64_000_000.toString(), consequence)
            assertEquals(400.toString(), probability)

            val remainingRisk = it["remainingRisk"]?.jsonObject

            val remainingConsequence = remainingRisk?.get("consequence")?.jsonPrimitive?.content
            val remainingProbability = remainingRisk?.get("probability")?.jsonPrimitive?.content

            assertEquals(3_200_000.toString(), remainingConsequence)
            assertEquals(20.toString(), remainingProbability)
        }

        val thirdScenario = scenarios?.get(2)?.jsonObject?.get("scenario")?.jsonObject

        thirdScenario?.let {
            val risk = it["risk"]?.jsonObject

            val consequence = risk?.get("consequence")?.jsonPrimitive?.content
            val probability = risk?.get("probability")?.jsonPrimitive?.content

            assertEquals(1_280_000_000.toString(), consequence)
            assertEquals(1.toString(), probability)

            val remainingRisk = it["remainingRisk"]?.jsonObject

            val remainingConsequence = remainingRisk?.get("consequence")?.jsonPrimitive?.content
            val remainingProbability = remainingRisk?.get("probability")?.jsonPrimitive?.content


            assertEquals(0.0025.toString(), remainingProbability)

            // Specific values not equal to the preset values should not be changed
            assertEquals(198_000.toString(), remainingConsequence)
        }

        assertEquals(true, migratedObject.migrationStatus?.migrationChanges)
        assertEquals(true, migratedObject.migrationStatus?.migrationRequiresNewApproval)
    }

    @Test
    fun `test migrate`() {
        val resourcePath = "3.2.json"
        val resourceUrl = object {}.javaClass.classLoader.getResource(resourcePath)

        val file = File(resourceUrl!!.toURI())
        val fileContent = file.readText()
        val obj =
            RiScContentResultDTO(
                riScId = "4",
                status = ContentStatus.Success,
                riScContent = fileContent,
                riScStatus = RiScStatus.Published,
                migrationStatus =
                    MigrationStatus(
                        migrationChanges = false,
                        migrationRequiresNewApproval = false,
                        migrationVersions = MigrationVersions(fromVersion = null, toVersion = null),
                    ),
            )
        val migratedObject = migrate(obj, latestSupportedVersion)

        val json = Json { ignoreUnknownKeys = true }
        val migratedJsonObject = json.parseToJsonElement(migratedObject.riScContent!!).jsonObject.toMap()

        val schemaVersion = migratedJsonObject["schemaVersion"]?.jsonPrimitive?.content
        assertEquals(latestSupportedVersion, schemaVersion)
    }
}
