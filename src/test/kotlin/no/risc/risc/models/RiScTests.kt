package no.risc.risc.models

import kotlinx.serialization.json.Json
import no.risc.validation.JSONValidator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.FieldSource
import java.io.File

class RiScTests {
    fun riSc4XWithoutValuations(schemaVersion: RiScVersion.RiSc4XVersion): RiSc4X =
        RiSc4X(
            schemaVersion = schemaVersion,
            title = "Title",
            scope = "Scope",
            scenarios =
                listOf(
                    RiSc4XScenario(
                        title = "Scenario with all attributes",
                        id = "ABCDE",
                        description = "Description",
                        url = "https://example.org",
                        threatActors =
                            listOf(
                                RiScScenarioThreatActor.HACKTIVIST,
                                RiScScenarioThreatActor.INSIDER,
                                RiScScenarioThreatActor.NATION_OR_GOVERNMENT,
                                RiScScenarioThreatActor.ORGANISED_CRIME,
                                RiScScenarioThreatActor.RECKLESS_EMPLOYEE,
                                RiScScenarioThreatActor.SCRIPT_KIDDIE,
                                RiScScenarioThreatActor.TERRORIST_ORGANISATION,
                            ),
                        vulnerabilities =
                            listOf(
                                RiSc4XScenarioVulnerability.EXCESSIVE_USE,
                                RiSc4XScenarioVulnerability.INFORMATION_LEAK,
                                RiSc4XScenarioVulnerability.UNAUTHORIZED_ACCESS,
                                RiSc4XScenarioVulnerability.UNMONITORED_USE,
                                RiSc4XScenarioVulnerability.INPUT_TAMPERING,
                                RiSc4XScenarioVulnerability.MISCONFIGURATION,
                                RiSc4XScenarioVulnerability.DEPENDENCY_VULNERABILITY,
                                RiSc4XScenarioVulnerability.FLAWED_DESIGN,
                            ),
                        risk = RiScScenarioRisk(summary = "Text", probability = 1.0, consequence = 200_000.0),
                        remainingRisk = RiScScenarioRisk(summary = "Text", probability = 0.025, consequence = 10_000.0),
                        actions =
                            listOf(
                                RiSc4XScenarioAction(
                                    title = "Title",
                                    id = "12345",
                                    description = "Description",
                                    url = "https://example.org",
                                    status = RiScScenarioActionStatusV4.COMPLETED,
                                ),
                                RiSc4XScenarioAction(
                                    title = "Title",
                                    id = "23456",
                                    description = "Description",
                                    status = RiScScenarioActionStatusV4.ABORTED,
                                ),
                                RiSc4XScenarioAction(
                                    title = "Title",
                                    id = "34567",
                                    description = "Description",
                                    url = "https://example.org",
                                    status = RiScScenarioActionStatusV4.NOT_STARTED,
                                ),
                                RiSc4XScenarioAction(
                                    title = "Title",
                                    id = "45678",
                                    description = "Description",
                                    url = "https://example.org",
                                    status = RiScScenarioActionStatusV4.ON_HOLD,
                                ),
                                RiSc4XScenarioAction(
                                    title = "Title",
                                    id = "56789",
                                    description = "Description",
                                    url = "https://example.org",
                                    status = RiScScenarioActionStatusV4.IN_PROGRESS,
                                ),
                            ),
                    ),
                    RiSc4XScenario(
                        title = "Scenario with minimum attributes",
                        id = "ABCDE",
                        description = "Description",
                        threatActors = listOf(),
                        vulnerabilities = listOf(),
                        risk = RiScScenarioRisk(probability = 20.0, consequence = 40_000.0),
                        remainingRisk = RiScScenarioRisk(probability = 0.13, consequence = 25_000.0),
                        actions = listOf(),
                    ),
                ),
        )

    fun riSc4XWithValuations(schemaVersion: RiScVersion.RiSc4XVersion): RiSc4X =
        riSc4XWithoutValuations(schemaVersion).copy(
            valuations =
                listOf(
                    RiScValuation(
                        description = "Description",
                        confidentiality = RiScValuationConfidentiality.CONFIDENTIAL,
                        integrity = RiScValuationIntegrity.CRITICAL,
                        availability = RiScValuationAvailability.FOUR_HOURS,
                    ),
                    RiScValuation(
                        description = "Description",
                        confidentiality = RiScValuationConfidentiality.INTERNAL,
                        integrity = RiScValuationIntegrity.INSIGNIFICANT,
                        availability = RiScValuationAvailability.INSIGNIFICANT,
                    ),
                    RiScValuation(
                        description = "Description",
                        confidentiality = RiScValuationConfidentiality.PUBLIC,
                        integrity = RiScValuationIntegrity.EXPECTED,
                        availability = RiScValuationAvailability.IMMEDIATE,
                    ),
                    RiScValuation(
                        description = "Description",
                        confidentiality = RiScValuationConfidentiality.STRICTLY_CONFIDENTIAL,
                        integrity = RiScValuationIntegrity.DEPENDENT,
                        availability = RiScValuationAvailability.TWO_DAYS,
                    ),
                ),
        )

    @Test
    fun `test that RiSc4X without valuations validates correctly for both versions 4-0 and 4-1`() {
        assertTrue(
            JSONValidator
                .validateAgainstSchema(
                    riScId = "abcde",
                    schema = "4.0",
                    riScContent = Json.encodeToString(riSc4XWithoutValuations(RiScVersion.RiSc4XVersion.VERSION_4_0)),
                ).isValid,
            "All choices of missing or present attributes in the RiSc model should validate correctly with the JSON schema for version 4.0 when valuations are not present.",
        )

        assertTrue(
            JSONValidator
                .validateAgainstSchema(
                    riScId = "abcde",
                    schema = "4.1",
                    riScContent = Json.encodeToString(riSc4XWithoutValuations(RiScVersion.RiSc4XVersion.VERSION_4_1)),
                ).isValid,
            "All choices of missing or present attributes in the RiSc model should validate correctly with the JSON schema for version 4.1 when valuations are not present.",
        )
    }

    @Test
    fun `test that RiSc4X with valuations validates correctly for both versions 4-0 and 4-1`() {
        assertTrue(
            JSONValidator
                .validateAgainstSchema(
                    riScId = "abcde",
                    schema = "4.0",
                    riScContent = Json.encodeToString(riSc4XWithValuations(RiScVersion.RiSc4XVersion.VERSION_4_0)),
                ).isValid,
            "All choices of missing or present attributes in the RiSc model should validate correctly with the JSON schema for version 4.0 when valuations are present.",
        )

        assertTrue(
            JSONValidator
                .validateAgainstSchema(
                    riScId = "abcde",
                    schema = "4.1",
                    riScContent = Json.encodeToString(riSc4XWithValuations(RiScVersion.RiSc4XVersion.VERSION_4_1)),
                ).isValid,
            "All choices of missing or present attributes in the RiSc model should validate correctly with the JSON schema for version 4.1 when valuations are present.",
        )
    }

    fun riSc3XWithoutValuations(schemaVersion: RiScVersion.RiSc3XVersion): RiSc3X =
        RiSc3X(
            schemaVersion = schemaVersion,
            title = "Title",
            scope = "Scope",
            scenarios =
                listOf(
                    RiSc3XScenario(
                        title = "Scenario with all attributes",
                        id = "ABCDE",
                        description = "Description",
                        url = "https://example.org",
                        threatActors =
                            listOf(
                                RiScScenarioThreatActor.HACKTIVIST,
                                RiScScenarioThreatActor.INSIDER,
                                RiScScenarioThreatActor.NATION_OR_GOVERNMENT,
                                RiScScenarioThreatActor.ORGANISED_CRIME,
                                RiScScenarioThreatActor.RECKLESS_EMPLOYEE,
                                RiScScenarioThreatActor.SCRIPT_KIDDIE,
                                RiScScenarioThreatActor.TERRORIST_ORGANISATION,
                            ),
                        vulnerabilities =
                            listOf(
                                RiSc3XScenarioVulnerability.USER_REPUDIATION,
                                RiSc3XScenarioVulnerability.INFORMATION_LEAK,
                                RiSc3XScenarioVulnerability.DISCLOSED_SECRET,
                                RiSc3XScenarioVulnerability.COMPROMISED_ADMIN_USER,
                                RiSc3XScenarioVulnerability.INPUT_TAMPERING,
                                RiSc3XScenarioVulnerability.MISCONFIGURATION,
                                RiSc3XScenarioVulnerability.DEPENDENCY_VULNERABILITY,
                                RiSc3XScenarioVulnerability.DENIAL_OF_SERVICE,
                                RiSc3XScenarioVulnerability.ESCALATION_OF_RIGHTS,
                            ),
                        risk = RiScScenarioRisk(summary = "Text", probability = 1.0, consequence = 200_000.0),
                        remainingRisk = RiScScenarioRisk(summary = "Text", probability = 0.025, consequence = 10_000.0),
                        actions =
                            listOf(
                                RiSc3XScenarioAction(
                                    title = "Title",
                                    id = "12345",
                                    description = "Description",
                                    url = "https://example.org",
                                    status = RiScScenarioActionStatusV4.COMPLETED,
                                    owner = "Ola Nordmann",
                                    deadline = "2024-12-12",
                                ),
                                RiSc3XScenarioAction(
                                    title = "Title",
                                    id = "23456",
                                    description = "Description",
                                    status = RiScScenarioActionStatusV4.ABORTED,
                                    owner = "Ola Nordmann",
                                ),
                                RiSc3XScenarioAction(
                                    title = "Title",
                                    id = "34567",
                                    description = "Description",
                                    url = "https://example.org",
                                    status = RiScScenarioActionStatusV4.NOT_STARTED,
                                    deadline = "2020-10-10",
                                ),
                                RiSc3XScenarioAction(
                                    title = "Title",
                                    id = "45678",
                                    description = "Description",
                                    url = "https://example.org",
                                    status = RiScScenarioActionStatusV4.ON_HOLD,
                                ),
                                RiSc3XScenarioAction(
                                    title = "Title",
                                    id = "56789",
                                    description = "Description",
                                    url = "https://example.org",
                                    status = RiScScenarioActionStatusV4.IN_PROGRESS,
                                ),
                            ),
                        existingActions = "Existing actions",
                    ),
                    RiSc3XScenario(
                        title = "Scenario with minimum attributes",
                        id = "ABCDE",
                        description = "Description",
                        threatActors = listOf(),
                        vulnerabilities = listOf(),
                        risk = RiScScenarioRisk(probability = 20.0, consequence = 40_000.0),
                        remainingRisk = RiScScenarioRisk(probability = 0.13, consequence = 25_000.0),
                        actions = listOf(),
                    ),
                ),
        )

    fun riSc3XWithValuations(schemaVersion: RiScVersion.RiSc3XVersion): RiSc3X =
        riSc3XWithoutValuations(schemaVersion).copy(
            valuations =
                listOf(
                    RiScValuation(
                        description = "Description",
                        confidentiality = RiScValuationConfidentiality.CONFIDENTIAL,
                        integrity = RiScValuationIntegrity.CRITICAL,
                        availability = RiScValuationAvailability.FOUR_HOURS,
                    ),
                    RiScValuation(
                        description = "Description",
                        confidentiality = RiScValuationConfidentiality.INTERNAL,
                        integrity = RiScValuationIntegrity.INSIGNIFICANT,
                        availability = RiScValuationAvailability.INSIGNIFICANT,
                    ),
                    RiScValuation(
                        description = "Description",
                        confidentiality = RiScValuationConfidentiality.PUBLIC,
                        integrity = RiScValuationIntegrity.EXPECTED,
                        availability = RiScValuationAvailability.IMMEDIATE,
                    ),
                    RiScValuation(
                        description = "Description",
                        confidentiality = RiScValuationConfidentiality.STRICTLY_CONFIDENTIAL,
                        integrity = RiScValuationIntegrity.DEPENDENT,
                        availability = RiScValuationAvailability.TWO_DAYS,
                    ),
                ),
        )

    @Test
    fun `test that RiSc3X without valuations validates correctly`() {
        assertTrue(
            JSONValidator
                .validateAgainstSchema(
                    riScId = "abcde",
                    schema = "3.2",
                    riScContent = Json.encodeToString(riSc3XWithoutValuations(RiScVersion.RiSc3XVersion.VERSION_3_2)),
                ).isValid,
            "All choices of missing or present attributes in the RiSc model should validate correctly with the JSON schema for version 3.2 when valuations are not present.",
        )
        assertTrue(
            JSONValidator
                .validateAgainstSchema(
                    riScId = "abcde",
                    schema = "3.3",
                    riScContent = Json.encodeToString(riSc3XWithoutValuations(RiScVersion.RiSc3XVersion.VERSION_3_3)),
                ).isValid,
            "All choices of missing or present attributes in the RiSc model should validate correctly with the JSON schema for version 3.3 when valuations are not present.",
        )
    }

    @Test
    fun `test that RiSc3X with valuations validates correctly`() {
        assertTrue(
            JSONValidator
                .validateAgainstSchema(
                    riScId = "abcde",
                    schema = "3.2",
                    riScContent = Json.encodeToString(riSc3XWithValuations(RiScVersion.RiSc3XVersion.VERSION_3_2)),
                ).isValid,
            "All choices of missing or present attributes in the RiSc model should validate correctly with the JSON schema for version 3.2 when valuations are present.",
        )
        assertTrue(
            JSONValidator
                .validateAgainstSchema(
                    riScId = "abcde",
                    schema = "3.3",
                    riScContent = Json.encodeToString(riSc3XWithValuations(RiScVersion.RiSc3XVersion.VERSION_3_3)),
                ).isValid,
            "All choices of missing or present attributes in the RiSc model should validate correctly with the JSON schema for version 3.3 when valuations are present.",
        )
    }

    @ParameterizedTest
    @FieldSource("fromContentArguments")
    fun `test RiSc from content`(
        resourcePath: String,
        expectedVersion: RiScVersion,
    ) {
        val resourceUrl = object {}.javaClass.classLoader.getResource(resourcePath)
        val riScJSONString = File(resourceUrl!!.toURI()).readText()

        val riSc = RiSc.fromContent(riScJSONString)

        assertEquals(
            expectedVersion,
            riSc.schemaVersion,
            "The schema version of the parsed RiSc should be equal to the one in the JSON string.",
        )
    }

    companion object {
        val fromContentArguments: List<Arguments> =
            listOf(
                arguments("3.2.json", RiScVersion.RiSc3XVersion.VERSION_3_2),
                arguments("3.3.json", RiScVersion.RiSc3XVersion.VERSION_3_3),
                arguments("4.0.json", RiScVersion.RiSc4XVersion.VERSION_4_0),
                arguments("4.1.json", RiScVersion.RiSc4XVersion.VERSION_4_1),
            )
    }
}
