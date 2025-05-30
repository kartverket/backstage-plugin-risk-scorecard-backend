package no.risc.risc.models

import kotlinx.serialization.json.Json
import no.risc.validation.JSONValidator
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RiScTests {
    fun riSc4XWithoutValuations(schemaVersion: String): RiSc4X =
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
                                    status = RiScScenarioActionStatus.COMPLETED,
                                ),
                                RiSc4XScenarioAction(
                                    title = "Title",
                                    id = "23456",
                                    description = "Description",
                                    status = RiScScenarioActionStatus.ABORTED,
                                ),
                                RiSc4XScenarioAction(
                                    title = "Title",
                                    id = "34567",
                                    description = "Description",
                                    url = "https://example.org",
                                    status = RiScScenarioActionStatus.NOT_STARTED,
                                ),
                                RiSc4XScenarioAction(
                                    title = "Title",
                                    id = "45678",
                                    description = "Description",
                                    url = "https://example.org",
                                    status = RiScScenarioActionStatus.ON_HOLD,
                                ),
                                RiSc4XScenarioAction(
                                    title = "Title",
                                    id = "56789",
                                    description = "Description",
                                    url = "https://example.org",
                                    status = RiScScenarioActionStatus.IN_PROGRESS,
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

    fun riSc4XWithValuations(schemaVersion: String): RiSc4X =
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
                    riScContent = Json.encodeToString(riSc4XWithoutValuations("4.0")),
                ).isValid,
            "All choices of missing or present attributes in the RiSc model should validate correctly with the JSON schema for version 4.0 when valuations are not present.",
        )

        assertTrue(
            JSONValidator
                .validateAgainstSchema(
                    riScId = "abcde",
                    schema = "4.1",
                    riScContent = Json.encodeToString(riSc4XWithoutValuations("4.1")),
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
                    riScContent = Json.encodeToString(riSc4XWithValuations("4.0")),
                ).isValid,
            "All choices of missing or present attributes in the RiSc model should validate correctly with the JSON schema for version 4.0 when valuations are present.",
        )

        assertTrue(
            JSONValidator
                .validateAgainstSchema(
                    riScId = "abcde",
                    schema = "4.1",
                    riScContent = Json.encodeToString(riSc4XWithValuations("4.1")),
                ).isValid,
            "All choices of missing or present attributes in the RiSc model should validate correctly with the JSON schema for version 4.1 when valuations are present.",
        )
    }

    val riSc33WithoutValuations =
        RiSc33(
            schemaVersion = "3.3",
            title = "Title",
            scope = "Scope",
            scenarios =
                listOf(
                    RiSc33Scenario(
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
                                RiSc33ScenarioVulnerability.USER_REPUDIATION,
                                RiSc33ScenarioVulnerability.INFORMATION_LEAK,
                                RiSc33ScenarioVulnerability.DISCLOSED_SECRET,
                                RiSc33ScenarioVulnerability.COMPROMISED_ADMIN_USER,
                                RiSc33ScenarioVulnerability.INPUT_TAMPERING,
                                RiSc33ScenarioVulnerability.MISCONFIGURATION,
                                RiSc33ScenarioVulnerability.DEPENDENCY_VULNERABILITY,
                                RiSc33ScenarioVulnerability.DENIAL_OF_SERVICE,
                                RiSc33ScenarioVulnerability.ESCALATION_OF_RIGHTS,
                            ),
                        risk = RiScScenarioRisk(summary = "Text", probability = 1.0, consequence = 200_000.0),
                        remainingRisk = RiScScenarioRisk(summary = "Text", probability = 0.025, consequence = 10_000.0),
                        actions =
                            listOf(
                                RiSc33ScenarioAction(
                                    title = "Title",
                                    id = "12345",
                                    description = "Description",
                                    url = "https://example.org",
                                    status = RiScScenarioActionStatus.COMPLETED,
                                    owner = "Ola Nordmann",
                                    deadline = "2024-12-12",
                                ),
                                RiSc33ScenarioAction(
                                    title = "Title",
                                    id = "23456",
                                    description = "Description",
                                    status = RiScScenarioActionStatus.ABORTED,
                                    owner = "Ola Nordmann",
                                ),
                                RiSc33ScenarioAction(
                                    title = "Title",
                                    id = "34567",
                                    description = "Description",
                                    url = "https://example.org",
                                    status = RiScScenarioActionStatus.NOT_STARTED,
                                    deadline = "2020-10-10",
                                ),
                                RiSc33ScenarioAction(
                                    title = "Title",
                                    id = "45678",
                                    description = "Description",
                                    url = "https://example.org",
                                    status = RiScScenarioActionStatus.ON_HOLD,
                                ),
                                RiSc33ScenarioAction(
                                    title = "Title",
                                    id = "56789",
                                    description = "Description",
                                    url = "https://example.org",
                                    status = RiScScenarioActionStatus.IN_PROGRESS,
                                ),
                            ),
                        existingActions = "Existing actions",
                    ),
                    RiSc33Scenario(
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

    val riSc33WithValuations =
        riSc33WithoutValuations.copy(
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
    fun `test that RiSc33 without valuations validates correctly`() {
        assertTrue(
            JSONValidator
                .validateAgainstSchema(
                    riScId = "abcde",
                    schema = "3.3",
                    riScContent = Json.encodeToString(riSc33WithoutValuations),
                ).isValid,
            "All choices of missing or present attributes in the RiSc model should validate correctly with the JSON schema for version 3.3 when valuations are not present.",
        )
    }

    @Test
    fun `test that RiSc33 with valuations validates correctly`() {
        assertTrue(
            JSONValidator
                .validateAgainstSchema(
                    riScId = "abcde",
                    schema = "3.3",
                    riScContent = Json.encodeToString(riSc33WithValuations),
                ).isValid,
            "All choices of missing or present attributes in the RiSc model should validate correctly with the JSON schema for version 3.3 when valuations are present.",
        )
    }
}
