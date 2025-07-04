package no.risc.utils

import no.risc.risc.models.MigrationStatus
import no.risc.risc.models.MigrationVersions
import no.risc.risc.models.RiSc
import no.risc.risc.models.RiSc3X
import no.risc.risc.models.RiSc3XScenarioVulnerability
import no.risc.risc.models.RiSc4X
import no.risc.risc.models.RiSc4XScenarioVulnerability
import no.risc.risc.models.RiScScenarioRisk
import no.risc.risc.models.RiScVersion
import no.risc.risc.models.UnknownRiSc
import no.risc.utils.comparison.MigrationChange40
import no.risc.utils.comparison.MigrationChange40Action
import no.risc.utils.comparison.MigrationChange40Scenario
import no.risc.utils.comparison.MigrationChange41
import no.risc.utils.comparison.MigrationChange41Scenario
import no.risc.utils.comparison.MigrationChangedTypedValue
import no.risc.utils.comparison.MigrationChangedValue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.assertThrows
import java.io.File

class MigrationFunctionTests {
    private val latestSupportedVersion = "4.1"

    @Test
    fun `test migrateFrom32To33`() {
        val resourceUrl = object {}.javaClass.classLoader.getResource("3.2.json")

        val riSc = RiSc.fromContent(File(resourceUrl!!.toURI()).readText()) as RiSc3X
        val (migratedRiSc, _) =
            migrateFrom32To33(
                riSc = riSc,
                migrationStatus =
                    MigrationStatus(
                        migrationChanges = false,
                        migrationRequiresNewApproval = false,
                        migrationVersions = MigrationVersions(fromVersion = null, toVersion = null),
                    ),
            )

        assertEquals(
            RiScVersion.RiSc3XVersion.VERSION_3_3,
            migratedRiSc.schemaVersion,
            "The schema version should be updated when migrating to version 3.3.",
        )
        assertEquals(
            riSc,
            migratedRiSc.copy(schemaVersion = RiScVersion.RiSc3XVersion.VERSION_3_2),
            "The only change to the RiSc when migrating to version 3.3 should be the schema version.",
        )
    }

    @Test
    fun `test migrateFrom33To40`() {
        val resourceUrl = object {}.javaClass.classLoader.getResource("3.3.json")

        val riSc = RiSc.fromContent(File(resourceUrl!!.toURI()).readText()) as RiSc3X

        val (migratedRiSc, migrationStatus) =
            migrateFrom33To40(
                riSc = riSc,
                migrationStatus =
                    MigrationStatus(
                        migrationChanges = false,
                        migrationRequiresNewApproval = false,
                        migrationVersions = MigrationVersions(fromVersion = null, toVersion = null),
                    ),
            )

        assertEquals(
            RiScVersion.RiSc4XVersion.VERSION_4_0,
            migratedRiSc.schemaVersion,
            "The schema version should be updated when migrating to version 4.0",
        )
        assertEquals(true, migrationStatus.migrationChanges, "A migration from 3.3 to 4.0 should make changes.")
        assertEquals(
            true,
            migrationStatus.migrationRequiresNewApproval,
            "A migration from 3.3 to 4.0 should require new approval.",
        )

        migratedRiSc.scenarios.forEachIndexed { index, scenario ->
            val expectedVulnerabilities =
                if (index == 0) {
                    listOf(
                        RiSc4XScenarioVulnerability.UNMONITORED_USE,
                        RiSc4XScenarioVulnerability.UNAUTHORIZED_ACCESS,
                        RiSc4XScenarioVulnerability.INFORMATION_LEAK,
                        RiSc4XScenarioVulnerability.EXCESSIVE_USE,
                        RiSc4XScenarioVulnerability.MISCONFIGURATION,
                    )
                } else {
                    listOf(RiSc4XScenarioVulnerability.MISCONFIGURATION)
                }

            assertEquals(
                expectedVulnerabilities,
                scenario.vulnerabilities,
                "Vulnerabilities should be properly mapped when migrating from 3.3 to 4.0.",
            )
        }

        assertNotNull(
            migrationStatus.migrationChanges40,
            "When changes have been made, there should be a migration changes object.",
        )

        val changedScenarios = migrationStatus.migrationChanges40.scenarios

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
                MigrationChangedTypedValue(
                    RiSc3XScenarioVulnerability.USER_REPUDIATION,
                    RiSc4XScenarioVulnerability.UNMONITORED_USE,
                ),
                MigrationChangedTypedValue(
                    RiSc3XScenarioVulnerability.COMPROMISED_ADMIN_USER,
                    RiSc4XScenarioVulnerability.UNAUTHORIZED_ACCESS,
                ),
                MigrationChangedTypedValue(
                    RiSc3XScenarioVulnerability.ESCALATION_OF_RIGHTS,
                    RiSc4XScenarioVulnerability.UNAUTHORIZED_ACCESS,
                ),
                MigrationChangedTypedValue(
                    RiSc3XScenarioVulnerability.DISCLOSED_SECRET,
                    RiSc4XScenarioVulnerability.INFORMATION_LEAK,
                ),
                MigrationChangedTypedValue(
                    RiSc3XScenarioVulnerability.DENIAL_OF_SERVICE,
                    RiSc4XScenarioVulnerability.EXCESSIVE_USE,
                ),
            )

        assertTrue(
            { changedScenario.changedVulnerabilities.containsAll(expectedChanges) },
            "All changes, even those going to the same new vulnerability value should be included in the changes.",
        )
    }

    @Test
    fun `test migrate33To40NoScenarios`() {
        val resourceUrl = object {}.javaClass.classLoader.getResource("3.3-no-scenarios.json")
        val riSc = RiSc.fromContent(File(resourceUrl!!.toURI()).readText()) as RiSc3X
        val (migratedRiSc, migrationStatus) =
            migrateFrom33To40(
                riSc = riSc,
                migrationStatus =
                    MigrationStatus(
                        migrationChanges = false,
                        migrationRequiresNewApproval = true,
                        migrationVersions = MigrationVersions(fromVersion = null, toVersion = null),
                    ),
            )

        assertEquals(
            RiScVersion.RiSc4XVersion.VERSION_4_0,
            migratedRiSc.schemaVersion,
            "The schema version should be updated when migrating to version 4.0.",
        )

        assertNull(
            migrationStatus.migrationChanges40,
            "When no changes have been made, there should be no migration changes object.",
        )
    }

    @Test
    fun `test migrateFrom40To41`() {
        val resourceUrl = object {}.javaClass.classLoader.getResource("4.0.json")

        val riSc = RiSc.fromContent(File(resourceUrl!!.toURI()).readText()) as RiSc4X
        val (migratedRiSc, migrationStatus) =
            migrateFrom40To41(
                riSc = riSc,
                migrationStatus =
                    MigrationStatus(
                        migrationChanges = false,
                        migrationRequiresNewApproval = false,
                        migrationVersions = MigrationVersions(fromVersion = null, toVersion = null),
                    ),
            )

        // Check that schema version is set to 4.1
        assertEquals(
            RiScVersion.RiSc4XVersion.VERSION_4_1,
            migratedRiSc.schemaVersion,
            "The schema version should be updated when migrating to version 4.1.",
        )

        fun testConsequenceAndProbability(
            risk: RiScScenarioRisk,
            expectedConsequence: Number,
            expectedProbability: Number,
        ) {
            assertEquals(expectedConsequence, risk.consequence)
            assertEquals(expectedProbability, risk.probability)
        }

        // Verify that all 3 scenarios are present after migration.
        // This ensures that the following tests are run as expected.
        assertEquals(3, migratedRiSc.scenarios.size, "All scenarios should be present after migration.")

        testConsequenceAndProbability(
            risk = migratedRiSc.scenarios[0].risk,
            expectedConsequence = 160_000.0,
            expectedProbability = 0.05,
        )
        testConsequenceAndProbability(
            risk = migratedRiSc.scenarios[0].remainingRisk,
            expectedConsequence = 8_000.0,
            expectedProbability = 0.0025,
        )

        testConsequenceAndProbability(
            risk = migratedRiSc.scenarios[1].risk,
            expectedConsequence = 64_000_000.0,
            expectedProbability = 20.0,
        )
        testConsequenceAndProbability(
            risk = migratedRiSc.scenarios[1].remainingRisk,
            expectedConsequence = 3_200_000.0,
            expectedProbability = 1.0,
        )

        testConsequenceAndProbability(
            risk = migratedRiSc.scenarios[2].risk,
            expectedConsequence = 1_280_000_000.0,
            expectedProbability = 400.0,
        )
        testConsequenceAndProbability(
            risk = migratedRiSc.scenarios[2].remainingRisk,
            expectedConsequence = 198_000.0,
            expectedProbability = 0.123,
        )

        assertEquals(true, migrationStatus.migrationChanges)
        assertEquals(true, migrationStatus.migrationRequiresNewApproval)

        assertNotNull(
            migrationStatus.migrationChanges41,
            "When changes have been made, there should be a migration changes object.",
        )

        val changedScenarios = migrationStatus.migrationChanges41.scenarios

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
        val resourceUrl = object {}.javaClass.classLoader.getResource("3.2.json")

        val riSc = RiSc.fromContent(File(resourceUrl!!.toURI()).readText()) as RiSc3X
        val (migratedRiSc, migrationStatus) = migrate(riSc = riSc, endVersion = latestSupportedVersion)

        assertEquals(RiScVersion.fromString(latestSupportedVersion), migratedRiSc.schemaVersion)

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
                                    MigrationChangedTypedValue(
                                        RiSc3XScenarioVulnerability.DENIAL_OF_SERVICE,
                                        RiSc4XScenarioVulnerability.EXCESSIVE_USE,
                                    ),
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
            migrationStatus.migrationChanges40,
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
            migrationStatus.migrationChanges41,
            "Changes made from version 4.0 to 4.1 should be included.",
        )
    }

    @Test
    fun `test migrate throws error on unsupported version`() {
        assertThrows<IllegalStateException>("If an unsupported version is provided, the migrate function should throw an exception") {
            migrate(
                riSc =
                    RiSc4X(
                        schemaVersion = RiScVersion.RiSc4XVersion.VERSION_4_1,
                        title = "Title",
                        scope = "Scope",
                        valuations = emptyList(),
                        scenarios = emptyList(),
                    ),
                endVersion = "0.0",
            )
        }
    }

    @Test
    fun `test migrate throws error on unsupported RiSc`() {
        assertThrows<IllegalStateException>("If an unsupported RiSc is provided, the migrate function should throw an exception") {
            migrate(riSc = UnknownRiSc(content = ""), endVersion = "4.1")
        }
    }

    @Test
    fun `test migrate throws error on migration to lower version`() {
        assertThrows<IllegalStateException>(
            "If the supplied RiSc has a newer version than the end version, the migrate function should throw an exception",
        ) {
            migrate(
                riSc =
                    RiSc4X(
                        schemaVersion = RiScVersion.RiSc4XVersion.VERSION_4_1,
                        title = "Title",
                        scope = "Scope",
                        valuations = emptyList(),
                        scenarios = emptyList(),
                    ),
                endVersion = "3.3",
            )
        }
    }
}
