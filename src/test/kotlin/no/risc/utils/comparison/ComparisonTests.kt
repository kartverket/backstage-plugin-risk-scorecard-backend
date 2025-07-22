package no.risc.utils.comparison

import no.risc.exception.exceptions.DifferenceException
import no.risc.risc.models.MigrationVersions
import no.risc.risc.models.RiSc3X
import no.risc.risc.models.RiSc3XScenario
import no.risc.risc.models.RiSc3XScenarioAction
import no.risc.risc.models.RiSc4X
import no.risc.risc.models.RiSc4XScenario
import no.risc.risc.models.RiSc4XScenarioAction
import no.risc.risc.models.RiSc4XScenarioVulnerability
import no.risc.risc.models.RiScScenarioActionStatusV4
import no.risc.risc.models.RiScScenarioRisk
import no.risc.risc.models.RiScScenarioThreatActor
import no.risc.risc.models.RiScValuation
import no.risc.risc.models.RiScValuationAvailability
import no.risc.risc.models.RiScValuationConfidentiality
import no.risc.risc.models.RiScValuationIntegrity
import no.risc.risc.models.RiScVersion
import no.risc.risc.models.UnknownRiSc
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.assertThrows

class ComparisonTests {
    fun riScVersion32() =
        RiSc3X(
            schemaVersion = RiScVersion.RiSc3XVersion.VERSION_3_2,
            title = "RiSc version 3",
            scope = "A RiSc of version 3.2",
            valuations =
                listOf(
                    RiScValuation(
                        description = "Valuation",
                        confidentiality = RiScValuationConfidentiality.INTERNAL,
                        integrity = RiScValuationIntegrity.DEPENDENT,
                        availability = RiScValuationAvailability.TWO_DAYS,
                    ),
                ),
            scenarios =
                listOf(
                    RiSc3XScenario(
                        title = "Scenario 1",
                        id = "abcde",
                        description = "Description",
                        threatActors = listOf(),
                        vulnerabilities = listOf(),
                        risk = RiScScenarioRisk(probability = 1.0, consequence = 1_000.0),
                        remainingRisk = RiScScenarioRisk(probability = 0.01, consequence = 1_000_000.0),
                        actions = listOf(),
                        existingActions = "Existing actions",
                    ),
                ),
        )

    fun riScVersion33() =
        RiSc3X(
            schemaVersion = RiScVersion.RiSc3XVersion.VERSION_3_3,
            title = "RiSc version 3",
            scope = "A RiSc of version 3.3",
            valuations =
                listOf(
                    RiScValuation(
                        description = "Valuation 2",
                        confidentiality = RiScValuationConfidentiality.INTERNAL,
                        integrity = RiScValuationIntegrity.DEPENDENT,
                        availability = RiScValuationAvailability.TWO_DAYS,
                    ),
                ),
            scenarios =
                listOf(
                    RiSc3XScenario(
                        title = "Scenario 1",
                        id = "abcde",
                        description = "Description",
                        threatActors = listOf(),
                        vulnerabilities = listOf(),
                        url = "https://example.com",
                        risk = RiScScenarioRisk(probability = 3.0, consequence = 3_000.0),
                        remainingRisk = RiScScenarioRisk(probability = 0.01, consequence = 1_000_000.0),
                        actions =
                            listOf(
                                RiSc3XScenarioAction(
                                    title = "Title",
                                    id = "bbbbb",
                                    description = "",
                                    status = RiScScenarioActionStatusV4.ON_HOLD,
                                    deadline = "2025-10-25",
                                    owner = "Ola Nordmann",
                                ),
                            ),
                        existingActions = "Existing actions",
                    ),
                    RiSc3XScenario(
                        title = "Scenario 2",
                        id = "aaaaa",
                        description = "Description",
                        threatActors = listOf(),
                        vulnerabilities = listOf(),
                        risk = RiScScenarioRisk(probability = 50.0, consequence = 1_000.0),
                        remainingRisk = RiScScenarioRisk(probability = 0.01, consequence = 1_000_000.0),
                        actions = listOf(),
                        existingActions = "Existing actions",
                    ),
                ),
        )

    fun riScVersion40() =
        RiSc4X(
            schemaVersion = RiScVersion.RiSc4XVersion.VERSION_4_0,
            title = "RiSc version 4",
            scope = "A RiSc",
            valuations =
                listOf(
                    RiScValuation(
                        description = "Valuation 2",
                        confidentiality = RiScValuationConfidentiality.INTERNAL,
                        integrity = RiScValuationIntegrity.DEPENDENT,
                        availability = RiScValuationAvailability.TWO_DAYS,
                    ),
                ),
            scenarios =
                listOf(
                    RiSc4XScenario(
                        title = "Scenario 1 title",
                        id = "abcde",
                        description = "Description of scenario",
                        threatActors = listOf(RiScScenarioThreatActor.SCRIPT_KIDDIE),
                        vulnerabilities =
                            listOf(
                                RiSc4XScenarioVulnerability.EXCESSIVE_USE,
                                RiSc4XScenarioVulnerability.INFORMATION_LEAK,
                            ),
                        risk = RiScScenarioRisk(probability = 3.0, consequence = 1_000.0),
                        remainingRisk = RiScScenarioRisk(probability = 0.01, consequence = 2_000_000.0),
                        actions =
                            listOf(
                                RiSc4XScenarioAction(
                                    title = "Title of action",
                                    id = "bbbbb",
                                    description = "Description",
                                    status = RiScScenarioActionStatusV4.IN_PROGRESS,
                                ),
                            ),
                    ),
                    RiSc4XScenario(
                        title = "Scenario 2",
                        id = "aaaaa",
                        description = "Description",
                        threatActors = listOf(),
                        vulnerabilities = listOf(),
                        risk = RiScScenarioRisk(probability = 50.0, consequence = 1_000.0),
                        remainingRisk = RiScScenarioRisk(probability = 0.01, consequence = 1_000_000.0),
                        actions = listOf(),
                    ),
                ),
        )

    fun riScVersion41() =
        RiSc4X(
            schemaVersion = RiScVersion.RiSc4XVersion.VERSION_4_1,
            title = "RiSc version 4",
            scope = "A RiSc",
            valuations = listOf(),
            scenarios =
                listOf(
                    RiSc4XScenario(
                        title = "Scenario 2",
                        id = "aaaaa",
                        description = "Description",
                        threatActors = listOf(),
                        vulnerabilities = listOf(),
                        risk = RiScScenarioRisk(probability = 50.0, consequence = 8_000.0),
                        remainingRisk = RiScScenarioRisk(probability = 0.0025, consequence = 3_200_000.0),
                        actions = listOf(),
                    ),
                    RiSc4XScenario(
                        title = "Scenario 1",
                        id = "ccccc",
                        description = "Description",
                        threatActors = listOf(),
                        vulnerabilities = listOf(),
                        risk = RiScScenarioRisk(probability = 50.0, consequence = 1_000.0),
                        remainingRisk = RiScScenarioRisk(probability = 0.01, consequence = 1_000_000.0),
                        actions = listOf(),
                    ),
                ),
        )

    @Test
    fun `test compare unknown version of updated RiSc throws exception`() {
        val updatedRiSc = UnknownRiSc(content = "{}")
        val oldRiSc = UnknownRiSc(content = "{}")

        assertThrows<DifferenceException>(
            message = "Compare does not know what to do when the updated RiSc has an unknown version and should throw an exception.",
        ) { compare(updatedRiSc = updatedRiSc, oldRiSc = oldRiSc) }
    }

    @Test
    fun `test compare unknown version of old RiSc throws exception`() {
        val updatedRiSc = riScVersion33()
        val oldRiSc = UnknownRiSc(content = "{}")

        assertThrows<DifferenceException>(
            message = "Compare does not know what to do when the old RiSc has an unknown version and should throw an exception.",
        ) { compare(updatedRiSc = updatedRiSc, oldRiSc = oldRiSc) }
    }

    @Test
    fun `test compare updated RiSc has older version than old RiSc throws exception`() {
        val updatedRiSc = riScVersion32()
        val oldRiSc = riScVersion33()

        assertThrows<DifferenceException>(
            message = "Compare should throw an exception if the version has been downgraded in the updated RiSc.",
        ) { compare(updatedRiSc = updatedRiSc, oldRiSc = oldRiSc) }
    }

    @Test
    fun `test compare RiSc version 3-3 to 3-2`() {
        val updatedRiSc = riScVersion33()
        val oldRiSc = riScVersion32()

        val comparisonResult = compare(updatedRiSc = updatedRiSc, oldRiSc = oldRiSc)

        assertEquals(
            MigrationVersions(fromVersion = "3.2", toVersion = "3.3"),
            comparisonResult.migrationChanges.migrationVersions,
            "The comparison should migrate the old RiSc from version 3.2 to version 3.3.",
        )

        assertNull(
            comparisonResult.migrationChanges.migrationChanges40,
            "No migration to version 4.0 should be performed when the updated RiSc has version 3.3.",
        )

        assertNull(
            comparisonResult.migrationChanges.migrationChanges41,
            "No migration to version 4.1 should be performed when the updated RiSc has version 3.3.",
        )

        assertTrue(
            comparisonResult is RiSc3XChange,
            "The comparison for a version 3.X RiSc should be of type RiSc3XChange",
        )

        val comparisonChanges = comparisonResult as RiSc3XChange

        assertNull(
            comparisonChanges.title,
            "No changes have been made to the title field of the RiSc, and it should not be included in the changes.",
        )

        assertEquals(
            ChangedProperty<String, String>(oldValue = "A RiSc of version 3.2", newValue = "A RiSc of version 3.3"),
            comparisonChanges.scope,
            "The scope has been updated and the change should be included.",
        )

        assertEquals(
            listOf<TrackedProperty<RiScValuation, RiScValuation>>(
                DeletedProperty(
                    oldValue = oldRiSc.valuations!!.first(),
                ),
                AddedProperty(
                    newValue = updatedRiSc.valuations!!.first(),
                ),
            ),
            comparisonChanges.valuations,
            "There have been changes to the valuations. All old ones no longer present and new ones should be included.",
        )

        assertEquals(
            listOf<TrackedProperty<RiSc3XScenarioChange, RiSc3XScenario>>(
                ContentChangedProperty(
                    RiSc3XScenarioChange(
                        title = UnchangedProperty(value = "Scenario 1"),
                        id = "abcde",
                        description = UnchangedProperty(value = "Description"),
                        url = ChangedProperty(oldValue = null, newValue = "https://example.com"),
                        threatActors = listOf(),
                        vulnerabilities = listOf(),
                        risk =
                            ContentChangedProperty(
                                value =
                                    RiScScenarioRiskChange(
                                        probability = ChangedProperty(oldValue = 1.0, newValue = 3.0),
                                        consequence = ChangedProperty(oldValue = 1_000.0, newValue = 3_000.0),
                                    ),
                            ),
                        remainingRisk =
                            ContentChangedProperty(
                                value =
                                    RiScScenarioRiskChange(
                                        probability = UnchangedProperty(value = 0.01),
                                        consequence = UnchangedProperty(value = 1_000_000.0),
                                    ),
                            ),
                        actions =
                            listOf(
                                AddedProperty(
                                    newValue =
                                        RiSc3XScenarioAction(
                                            title = "Title",
                                            id = "bbbbb",
                                            description = "",
                                            status = RiScScenarioActionStatusV4.ON_HOLD,
                                            deadline = "2025-10-25",
                                            owner = "Ola Nordmann",
                                        ),
                                ),
                            ),
                    ),
                ),
                AddedProperty(
                    newValue =
                        RiSc3XScenario(
                            title = "Scenario 2",
                            id = "aaaaa",
                            description = "Description",
                            threatActors = listOf(),
                            vulnerabilities = listOf(),
                            risk = RiScScenarioRisk(probability = 50.0, consequence = 1_000.0),
                            remainingRisk = RiScScenarioRisk(probability = 0.01, consequence = 1_000_000.0),
                            actions = listOf(),
                            existingActions = "Existing actions",
                        ),
                ),
            ),
            comparisonChanges.scenarios,
            "The changes to the scenarios should include the changes to scenario 'acbde' and the new scenario 'aaaaa'.",
        )
    }

    @Test
    fun `test compare RiSc version 4-0 to 3-3`() {
        val updatedRiSc = riScVersion40()
        val oldRiSc = riScVersion33()

        val comparisonResult = compare(updatedRiSc = updatedRiSc, oldRiSc = oldRiSc)

        assertEquals(
            MigrationVersions(fromVersion = "3.3", toVersion = "4.0"),
            comparisonResult.migrationChanges.migrationVersions,
            "The comparison should migrate the old RiSc from version 3.3 to version 4.0.",
        )

        assertEquals(
            MigrationChange40(
                scenarios =
                    listOf(
                        MigrationChange40Scenario(
                            title = "Scenario 1",
                            id = "abcde",
                            removedExistingActions = "Existing actions",
                            changedVulnerabilities = listOf(),
                            changedActions =
                                listOf(
                                    MigrationChange40Action(
                                        title = "Title",
                                        id = "bbbbb",
                                        removedOwner = "Ola Nordmann",
                                        removedDeadline = "2025-10-25",
                                    ),
                                ),
                        ),
                        MigrationChange40Scenario(
                            title = "Scenario 2",
                            id = "aaaaa",
                            removedExistingActions = "Existing actions",
                            changedVulnerabilities = listOf(),
                            changedActions = listOf(),
                        ),
                    ),
            ),
            comparisonResult.migrationChanges.migrationChanges40,
            "The migration changes from version 3.3 to 4.0 should be included in the changes.",
        )

        assertNull(
            comparisonResult.migrationChanges.migrationChanges41,
            "No migration to version 4.1 should be performed when the updated RiSc has version 4.0.",
        )

        assertTrue(
            comparisonResult is RiSc4XChange,
            "The comparison for a version 4.X RiSc should be of type RiSc4XChange",
        )

        val comparisonChanges = comparisonResult as RiSc4XChange

        assertEquals(
            ChangedProperty<String, String>(oldValue = "RiSc version 3", newValue = "RiSc version 4"),
            comparisonChanges.title,
            "The title has been updated and the change should be included.",
        )

        assertEquals(
            ChangedProperty<String, String>(oldValue = "A RiSc of version 3.3", newValue = "A RiSc"),
            comparisonChanges.scope,
            "The scope has been updated and the change should be included.",
        )

        assertEquals(
            emptyList<SimpleTrackedProperty<RiScValuation>>(),
            comparisonChanges.valuations,
            "No valuations were changed, so there should be non included in the changes.",
        )

        assertEquals(
            listOf<TrackedProperty<RiSc4XScenarioChange, RiSc4XScenario>>(
                ContentChangedProperty(
                    RiSc4XScenarioChange(
                        title = ChangedProperty(oldValue = "Scenario 1", newValue = "Scenario 1 title"),
                        id = "abcde",
                        description = ChangedProperty(oldValue = "Description", newValue = "Description of scenario"),
                        url = ChangedProperty(oldValue = "https://example.com", newValue = null),
                        threatActors = listOf(AddedProperty(newValue = RiScScenarioThreatActor.SCRIPT_KIDDIE)),
                        vulnerabilities =
                            listOf(
                                AddedProperty(newValue = RiSc4XScenarioVulnerability.EXCESSIVE_USE),
                                AddedProperty(newValue = RiSc4XScenarioVulnerability.INFORMATION_LEAK),
                            ),
                        risk =
                            ContentChangedProperty(
                                value =
                                    RiScScenarioRiskChange(
                                        probability = UnchangedProperty(value = 3.0),
                                        consequence = ChangedProperty(oldValue = 3_000.0, newValue = 1_000.0),
                                    ),
                            ),
                        remainingRisk =
                            ContentChangedProperty(
                                value =
                                    RiScScenarioRiskChange(
                                        probability = UnchangedProperty(value = 0.01),
                                        consequence = ChangedProperty(oldValue = 1_000_000.0, newValue = 2_000_000.0),
                                    ),
                            ),
                        actions =
                            listOf(
                                ContentChangedProperty(
                                    value =
                                        RiSc4XScenarioActionChange(
                                            title = ChangedProperty(oldValue = "Title", newValue = "Title of action"),
                                            id = "bbbbb",
                                            description = ChangedProperty(oldValue = "", newValue = "Description"),
                                            status =
                                                ChangedProperty(
                                                    oldValue = RiScScenarioActionStatusV4.ON_HOLD,
                                                    newValue = RiScScenarioActionStatusV4.IN_PROGRESS,
                                                ),
                                        ),
                                ),
                            ),
                    ),
                ),
            ),
            comparisonChanges.scenarios,
            "The changes to the scenarios should include the changes to scenario 'acbde'.",
        )
    }

    @Test
    fun `test compare RiSc version 4-1 to 4-0`() {
        val updatedRiSc = riScVersion41()
        val oldRiSc = riScVersion40()

        val comparisonResult = compare(updatedRiSc = updatedRiSc, oldRiSc = oldRiSc)

        assertEquals(
            MigrationVersions(fromVersion = "4.0", toVersion = "4.1"),
            comparisonResult.migrationChanges.migrationVersions,
            "The comparison should migrate the old RiSc from version 4.0 to version 4.1.",
        )

        assertNull(
            comparisonResult.migrationChanges.migrationChanges40,
            "There should be no migration changes to version 4.0, as this migration is not necessary.",
        )

        assertEquals(
            MigrationChange41(
                scenarios =
                    listOf(
                        MigrationChange41Scenario(
                            title = "Scenario 1 title",
                            id = "abcde",
                            changedRiskProbability = null,
                            changedRiskConsequence = MigrationChangedValue(1_000.0, 8_000.0),
                            changedRemainingRiskProbability = MigrationChangedValue(0.01, 0.0025),
                            changedRemainingRiskConsequence = null,
                        ),
                        MigrationChange41Scenario(
                            title = "Scenario 2",
                            id = "aaaaa",
                            changedRiskProbability = MigrationChangedValue(50.0, 20.0),
                            changedRiskConsequence = MigrationChangedValue(1_000.0, 8_000.0),
                            changedRemainingRiskProbability = MigrationChangedValue(0.01, 0.0025),
                            changedRemainingRiskConsequence = MigrationChangedValue(1_000_000.0, 3_200_000.0),
                        ),
                    ),
            ),
            comparisonResult.migrationChanges.migrationChanges41,
            "The migration changes from version 4.0 to 4.1 should be included in the changes.",
        )

        assertTrue(
            comparisonResult is RiSc4XChange,
            "The comparison for a version 4.X RiSc should be of type RiSc4XChange",
        )

        val comparisonChanges = comparisonResult as RiSc4XChange

        assertNull(
            comparisonChanges.title,
            "No changes have been made to the title field of the RiSc, and it should not be included in the changes.",
        )

        assertNull(
            comparisonChanges.scope,
            "No changes have been made to the scope field of the RiSc, and it should not be included in the changes.",
        )

        assertEquals(
            listOf<SimpleTrackedProperty<RiScValuation>>(
                DeletedProperty(
                    oldValue =
                        RiScValuation(
                            description = "Valuation 2",
                            confidentiality = RiScValuationConfidentiality.INTERNAL,
                            integrity = RiScValuationIntegrity.DEPENDENT,
                            availability = RiScValuationAvailability.TWO_DAYS,
                        ),
                ),
            ),
            comparisonChanges.valuations,
            "The deleted valuation should be included in the valuations changes.",
        )

        assertEquals(
            listOf<TrackedProperty<RiSc4XScenarioChange, RiSc4XScenario>>(
                DeletedProperty(
                    RiSc4XScenario(
                        title = "Scenario 1 title",
                        id = "abcde",
                        description = "Description of scenario",
                        threatActors = listOf(RiScScenarioThreatActor.SCRIPT_KIDDIE),
                        vulnerabilities =
                            listOf(
                                RiSc4XScenarioVulnerability.EXCESSIVE_USE,
                                RiSc4XScenarioVulnerability.INFORMATION_LEAK,
                            ),
                        risk = RiScScenarioRisk(probability = 3.0, consequence = 8_000.0),
                        remainingRisk = RiScScenarioRisk(probability = 0.0025, consequence = 2_000_000.0),
                        actions =
                            listOf(
                                RiSc4XScenarioAction(
                                    title = "Title of action",
                                    id = "bbbbb",
                                    description = "Description",
                                    status = RiScScenarioActionStatusV4.IN_PROGRESS,
                                ),
                            ),
                    ),
                ),
                ContentChangedProperty(
                    value =
                        RiSc4XScenarioChange(
                            title = UnchangedProperty("Scenario 2"),
                            id = "aaaaa",
                            description = UnchangedProperty("Description"),
                            threatActors = listOf(),
                            vulnerabilities = listOf(),
                            risk =
                                ContentChangedProperty(
                                    value =
                                        RiScScenarioRiskChange(
                                            probability = ChangedProperty(oldValue = 20.0, newValue = 50.0),
                                            consequence = UnchangedProperty(value = 8_000.0),
                                        ),
                                ),
                            remainingRisk =
                                ContentChangedProperty(
                                    value =
                                        RiScScenarioRiskChange(
                                            probability = UnchangedProperty(value = 0.0025),
                                            consequence = UnchangedProperty(value = 3_200_000.0),
                                        ),
                                ),
                            actions = listOf(),
                        ),
                ),
                AddedProperty(newValue = updatedRiSc.scenarios[1]),
            ),
            comparisonChanges.scenarios,
            "The changes to the scenarios should include the removal of scenario 'acbde', the new scenario 'ccccc' and the changes to scenario 'aaaaa'.",
        )
    }

    @Test
    fun `test compare RiSc version 4-1 to 3-2`() {
        val updatedRiSc = riScVersion41()
        val oldRiSc = riScVersion32()

        val comparisonResult = compare(updatedRiSc = updatedRiSc, oldRiSc = oldRiSc)

        assertEquals(
            MigrationVersions(fromVersion = "3.2", toVersion = "4.1"),
            comparisonResult.migrationChanges.migrationVersions,
            "The comparison should migrate the old RiSc from version 3.2 to version 4.1.",
        )

        assertEquals(
            MigrationChange40(
                scenarios =
                    listOf(
                        MigrationChange40Scenario(
                            title = "Scenario 1",
                            id = "abcde",
                            removedExistingActions = "Existing actions",
                            changedVulnerabilities = listOf(),
                            changedActions = listOf(),
                        ),
                    ),
            ),
            comparisonResult.migrationChanges.migrationChanges40,
            "The migration changes from version 3.3 to 4.0 should be included in the changes.",
        )

        assertEquals(
            MigrationChange41(
                scenarios =
                    listOf(
                        MigrationChange41Scenario(
                            title = "Scenario 1",
                            id = "abcde",
                            changedRiskProbability = null,
                            changedRiskConsequence = MigrationChangedValue(1_000.0, 8_000.0),
                            changedRemainingRiskProbability = MigrationChangedValue(0.01, 0.0025),
                            changedRemainingRiskConsequence = MigrationChangedValue(1_000_000.0, 3_200_000.0),
                        ),
                    ),
            ),
            comparisonResult.migrationChanges.migrationChanges41,
            "The migration changes from version 4.0 to 4.1 should be included in the changes.",
        )

        assertTrue(
            comparisonResult is RiSc4XChange,
            "The comparison for a version 4.X RiSc should be of type RiSc4XChange",
        )

        val comparisonChanges = comparisonResult as RiSc4XChange

        assertEquals(
            ChangedProperty<String, String>(oldValue = "RiSc version 3", newValue = "RiSc version 4"),
            comparisonChanges.title,
            "The title has been updated and the change should be included.",
        )

        assertEquals(
            ChangedProperty<String, String>(oldValue = "A RiSc of version 3.2", newValue = "A RiSc"),
            comparisonChanges.scope,
            "The scope has been updated and the change should be included.",
        )

        assertEquals(
            listOf<SimpleTrackedProperty<RiScValuation>>(
                DeletedProperty(
                    oldValue =
                        RiScValuation(
                            description = "Valuation",
                            confidentiality = RiScValuationConfidentiality.INTERNAL,
                            integrity = RiScValuationIntegrity.DEPENDENT,
                            availability = RiScValuationAvailability.TWO_DAYS,
                        ),
                ),
            ),
            comparisonChanges.valuations,
            "The deleted valuation should be included in the changes.",
        )

        assertEquals(
            listOf<TrackedProperty<RiSc4XScenarioChange, RiSc4XScenario>>(
                DeletedProperty(
                    oldValue =
                        RiSc4XScenario(
                            title = "Scenario 1",
                            id = "abcde",
                            description = "Description",
                            threatActors = listOf(),
                            vulnerabilities = listOf(),
                            risk = RiScScenarioRisk(probability = 1.0, consequence = 8_000.0),
                            remainingRisk = RiScScenarioRisk(probability = 0.0025, consequence = 3_200_000.0),
                            actions = listOf(),
                        ),
                ),
                AddedProperty(
                    newValue =
                        RiSc4XScenario(
                            title = "Scenario 2",
                            id = "aaaaa",
                            description = "Description",
                            threatActors = listOf(),
                            vulnerabilities = listOf(),
                            risk = RiScScenarioRisk(probability = 50.0, consequence = 8_000.0),
                            remainingRisk = RiScScenarioRisk(probability = 0.0025, consequence = 3_200_000.0),
                            actions = listOf(),
                        ),
                ),
                AddedProperty(
                    newValue =
                        RiSc4XScenario(
                            title = "Scenario 1",
                            id = "ccccc",
                            description = "Description",
                            threatActors = listOf(),
                            vulnerabilities = listOf(),
                            risk = RiScScenarioRisk(probability = 50.0, consequence = 1_000.0),
                            remainingRisk = RiScScenarioRisk(probability = 0.01, consequence = 1_000_000.0),
                            actions = listOf(),
                        ),
                ),
            ),
            comparisonChanges.scenarios,
            "The changes to the scenarios should include the removed scenario 'acbde' and the added scenarios 'aaaaa' and 'ccccc'.",
        )
    }
}
