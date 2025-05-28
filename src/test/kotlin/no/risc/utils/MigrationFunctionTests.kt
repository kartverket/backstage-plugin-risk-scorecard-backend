package no.risc.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.risc.risc.models.ContentStatus
import no.risc.risc.models.MigrationStatus
import no.risc.risc.models.MigrationVersions
import no.risc.risc.models.RiScContentResultDTO
import no.risc.risc.models.RiScStatus
import no.risc.utils.comparison.MigrationChange40
import no.risc.utils.comparison.MigrationChange40Action
import no.risc.utils.comparison.MigrationChange40Scenario
import no.risc.utils.comparison.MigrationChange41
import no.risc.utils.comparison.MigrationChange41Scenario
import no.risc.utils.comparison.MigrationChangedValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
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

        val scenarios = migratedJsonObject["scenarios"]?.jsonArray
        scenarios?.forEachIndexed { index, scenario ->
            val scenarioObject = scenario.jsonObject
            val scenarioDetails = scenarioObject["scenario"]?.jsonObject

            scenarioDetails?.let {
                // Check that existingActions has been removed
                assertFalse(it.containsKey("existingActions"))

                val vulnerabilitiesArray =
                    it["vulnerabilities"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()

                val expectedVulnerabilities =
                    if (index == 0) {
                        listOf(
                            "Unmonitored use",
                            "Unauthorized access",
                            "Information leak",
                            "Excessive use",
                            "Misconfiguration",
                        )
                    } else {
                        listOf("Misconfiguration")
                    }
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

            assertEquals(true, migratedObject.migrationStatus.migrationChanges)
            assertEquals(true, migratedObject.migrationStatus.migrationRequiresNewApproval)
        }

        assertNotNull(
            migratedObject.migrationStatus.migrationChanges40,
            "When changes have been made, there should be a migration changes object.",
        )

        val changedScenarios = migratedObject.migrationStatus.migrationChanges40.scenarios

        assertEquals(1, changedScenarios.size, "Only changed scenarios should be included in the migration changes.")

        val changedScenario = changedScenarios[0]

        assertEquals("14Kap", changedScenario.id, "The scenario with ID 14Kap should have changes made.")
        assertEquals(
            "Ondsinnet bruker ønsker å ta ned løsningen. ",
            changedScenario.title,
            "The scenario title should be included with the changes.",
        )

        // Removed existing actions
        assertEquals(
            "Ddos protection. ",
            changedScenario.removedExistingActions,
            "The removedExistingActions field should be equal to the removed string.",
        )

        // Changed actions
        assertEquals(
            1,
            changedScenario.changedActions.size,
            "Only changed actions should be included in the migration changes.",
        )
        val expectedChangedAction =
            MigrationChange40Action(
                title = "Innlogging",
                id = "w100Q",
                removedOwner = "Kåre",
                removedDeadline = "2024-06-12",
            )
        assertEquals(
            expectedChangedAction,
            changedScenario.changedActions[0],
            "The removed owner and deadline fields should be equal to the removed strings.",
        )

        // Changed vulnerabilities
        assertEquals(
            5,
            changedScenario.changedVulnerabilities.size,
            "Only changed vulnerabilities should be included in the migration changes.",
        )

        val expectedChanges =
            listOf(
                MigrationChangedValue("User repudiation", "Unmonitored use"),
                MigrationChangedValue("Compromised admin user", "Unauthorized access"),
                MigrationChangedValue("Escalation of rights", "Unauthorized access"),
                MigrationChangedValue("Disclosed secret", "Information leak"),
                MigrationChangedValue("Denial of service", "Excessive use"),
            )

        assertTrue(
            { changedScenario.changedVulnerabilities.containsAll(expectedChanges) },
            "All changes, even those going to the same new vulnerability value should be included in the changes.",
        )
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

        assertNull(
            migratedObject.migrationStatus.migrationChanges40,
            "When no changes have been made, there should be no migration changes object.",
        )
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

        fun testConsequenceAndProbability(
            risk: JsonObject?,
            expectedConsequence: Number,
            expectedProbability: Number,
        ) {
            val consequence = risk?.get("consequence")?.jsonPrimitive?.content
            val probability = risk?.get("probability")?.jsonPrimitive?.content

            assertEquals(expectedConsequence.toString(), consequence)
            assertEquals(expectedProbability.toString(), probability)
        }

        val scenariosJsonObjects =
            scenarios?.map {
                it.jsonObject["scenario"]?.jsonObject
            }

        // Verify that all 3 scenarios are present after migration.
        // This ensures that the following tests are run as expected.
        assertEquals(scenariosJsonObjects?.size, 3)

        scenariosJsonObjects?.get(0)?.let {
            testConsequenceAndProbability(it["risk"]?.jsonObject, 160_000.0, 0.05)
            testConsequenceAndProbability(it["remainingRisk"]?.jsonObject, 8_000.0, 0.0025)
        }
        scenariosJsonObjects?.get(1)?.let {
            testConsequenceAndProbability(it["risk"]?.jsonObject, 64_000_000.0, 20.0)
            testConsequenceAndProbability(it["remainingRisk"]?.jsonObject, 3_200_000.0, 1.0)
        }
        scenariosJsonObjects?.get(2)?.let {
            testConsequenceAndProbability(it["risk"]?.jsonObject, 1_280_000_000.0, 400.0)

            // Specific values not equal to the preset values should not be changed
            testConsequenceAndProbability(it["remainingRisk"]?.jsonObject, 198_000.0, 0.123)
        }

        assertEquals(true, migratedObject.migrationStatus.migrationChanges)
        assertEquals(true, migratedObject.migrationStatus.migrationRequiresNewApproval)

        assertNotNull(
            migratedObject.migrationStatus.migrationChanges41,
            "When changes have been made, there should be a migration changes object.",
        )

        val changedScenarios = migratedObject.migrationStatus.migrationChanges41.scenarios

        assertEquals(3, changedScenarios.size, "All changed scenarios should be included in the migration changes.")

        val expectedFirstScenarioChanges =
            MigrationChange41Scenario(
                title = "Ondsinnet bruker ønsker å ta ned løsningen. ",
                id = "14Kap",
                changedRiskProbability = MigrationChangedValue(0.1, 0.05),
                changedRiskConsequence = MigrationChangedValue(30_000.0, 160_000.0),
                changedRemainingRiskProbability = MigrationChangedValue(0.01, 0.0025),
                changedRemainingRiskConsequence = MigrationChangedValue(1_000.0, 8_000.0),
            )

        assertEquals(
            expectedFirstScenarioChanges,
            changedScenarios[0],
            "The changed values of the first scenario should be properly included.",
        )

        val expectedSecondScenarioChanges =
            MigrationChange41Scenario(
                title = "Ondsinnet bruker ønsker å ta ned løsningen. ",
                id = "25FcD",
                changedRiskProbability = MigrationChangedValue(50.0, 20.0),
                changedRiskConsequence = MigrationChangedValue(30_000_000.0, 64_000_000.0),
                changedRemainingRiskConsequence = MigrationChangedValue(1_000_000.0, 3_200_000.0),
            )

        assertEquals(
            expectedSecondScenarioChanges,
            changedScenarios[1],
            "The changed values of the second scenario should be properly included, but unchanged values should not.",
        )

        val expectedThirdScenarioChanges =
            MigrationChange41Scenario(
                title = "Ondsinnet bruker ønsker å ta ned løsningen. ",
                id = "2dsFd",
                changedRiskProbability = MigrationChangedValue(300.0, 400.0),
                changedRiskConsequence = MigrationChangedValue(1_000_000_000.0, 1_280_000_000.0),
            )

        assertEquals(
            expectedThirdScenarioChanges,
            changedScenarios[2],
            "The changed values of the third scenario should be properly included, but unchanged values should not.",
        )
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

        val expected40MigrationChanges =
            MigrationChange40(
                scenarios =
                    listOf(
                        MigrationChange40Scenario(
                            title = "Ondsinnet bruker ønsker å ta ned løsningen. ",
                            id = "14Kap",
                            removedExistingActions = "Ddos protection. ",
                            changedVulnerabilities =
                                listOf(
                                    MigrationChangedValue("Denial of service", "Excessive use"),
                                ),
                            changedActions =
                                listOf(
                                    MigrationChange40Action(
                                        title = "Innlogging",
                                        id = "w100Q",
                                        removedOwner = "Kåre",
                                        removedDeadline = "2024-06-12",
                                    ),
                                ),
                        ),
                    ),
            )
        assertEquals(
            expected40MigrationChanges,
            migratedObject.migrationStatus.migrationChanges40,
            "Changes made from version 3.3 to 4.0 should be included.",
        )

        val expected41MigrationChanges =
            MigrationChange41(
                scenarios =
                    listOf(
                        MigrationChange41Scenario(
                            title = "Ondsinnet bruker ønsker å ta ned løsningen. ",
                            id = "14Kap",
                            changedRiskConsequence = MigrationChangedValue(1_000.0, 8_000.0),
                            changedRemainingRiskConsequence = MigrationChangedValue(1_000.0, 8_000.0),
                            changedRemainingRiskProbability = MigrationChangedValue(0.1, 0.05),
                        ),
                    ),
            )
        assertEquals(
            expected41MigrationChanges,
            migratedObject.migrationStatus.migrationChanges41,
            "Changes made from version 4.0 to 4.1 should be included.",
        )
    }
}
