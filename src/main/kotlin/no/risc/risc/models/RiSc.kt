package no.risc.risc.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import no.risc.utils.FlattenSerializer

// Models the RiSc schema for version 4.*
@Serializable
data class RiSc(
    val schemaVersion: String,
    val title: String,
    val scope: String,
    val valuations: List<RiScValuation>?,
    val scenarios: List<RiScScenario>,
)

@Serializable
data class RiScValuation(
    val description: String,
    val confidentiality: RiScValuationConfidentiality,
    val integrity: RiScValuationIntegrity,
    val availability: RiScValuationAvailability,
)

@Serializable
enum class RiScValuationConfidentiality {
    @SerialName("Public")
    PUBLIC,

    @SerialName("Internal")
    INTERNAL,

    @SerialName("Confidential")
    CONFIDENTIAL,

    @SerialName("Strictly confidential")
    STRICTLY_CONFIDENTIAL,
}

@Serializable
enum class RiScValuationIntegrity {
    @SerialName("Insignificant")
    INSIGNIFICANT,

    @SerialName("Expected")
    EXPECTED,

    @SerialName("Dependent")
    DEPENDENT,

    @SerialName("Critical")
    CRITICAL,
}

@Serializable
enum class RiScValuationAvailability {
    @SerialName("Insignificant")
    INSIGNIFICANT,

    @SerialName("2 days")
    TWO_DAYS,

    @SerialName("4 hours")
    FOUR_HOURS,

    @SerialName("Immediate")
    IMMEDIATE,
}

object RiScScenarioSerializer : FlattenSerializer<RiScScenario>(
    serializer = RiScScenario.generatedSerializer(),
    flattenKey = "scenario",
    subKeys = listOf("ID", "description", "url", "threatActors", "vulnerabilities", "risk", "remainingRisk", "actions"),
)

@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
@Serializable(with = RiScScenarioSerializer::class)
data class RiScScenario(
    val title: String,
    @SerialName("ID")
    val id: String,
    val description: String,
    val url: String? = null,
    val threatActors: List<RiScScenarioThreatActor>,
    val vulnerabilities: List<RiScScenarioVulnerability>,
    val risk: RiScScenarioRisk,
    val remainingRisk: RiScScenarioRisk,
    val actions: List<RiScScenarioAction>,
)

@Serializable
enum class RiScScenarioThreatActor {
    @SerialName("Script kiddie")
    SCRIPT_KIDDIE,

    @SerialName("Hacktivist")
    HACKTIVIST,

    @SerialName("Reckless employee")
    RECKLESS_EMPLOYEE,

    @SerialName("Insider")
    INSIDER,

    @SerialName("Organised crime")
    ORGANISED_CRIME,

    @SerialName("Terrorist organisation")
    TERRORIST_ORGANISATION,

    @SerialName("Nation/government")
    NATION_OR_GOVERNMENT,
}

@Serializable
enum class RiScScenarioVulnerability {
    @SerialName("Flawed design")
    FLAWED_DESIGN,

    @SerialName("Misconfiguration")
    MISCONFIGURATION,

    @SerialName("Dependency vulnerability")
    DEPENDENCY_VULNERABILITY,

    @SerialName("Unauthorized access")
    UNAUTHORIZED_ACCESS,

    @SerialName("Unmonitored use")
    UNMONITORED_USE,

    @SerialName("Input tampering")
    INPUT_TAMPERING,

    @SerialName("Information leak")
    INFORMATION_LEAK,

    @SerialName("Excessive use")
    EXCESSIVE_USE,
}

@Serializable
data class RiScScenarioRisk(
    val summary: String? = null,
    val probability: Double,
    val consequence: Double,
)

object RiScScenarioActionSerializer : FlattenSerializer<RiScScenarioAction>(
    serializer = RiScScenarioAction.generatedSerializer(),
    flattenKey = "action",
    subKeys = listOf("ID", "url", "status"),
)

@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
@Serializable(with = RiScScenarioActionSerializer::class)
data class RiScScenarioAction(
    val title: String,
    @SerialName("ID")
    val id: String,
    val url: String? = null,
    val status: RiScScenarioActionStatus,
)

@Serializable
enum class RiScScenarioActionStatus {
    @SerialName("Not started")
    NOT_STARTED,

    @SerialName("In progress")
    IN_PROGRESS,

    @SerialName("On hold")
    ON_HOLD,

    @SerialName("Completed")
    COMPLETED,

    @SerialName("Aborted")
    ABORTED,
}
