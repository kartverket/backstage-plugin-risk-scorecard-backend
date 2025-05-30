package no.risc.risc.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.SealedSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import no.risc.utils.FlattenSerializer

/***************
 * VERSION 4.X *
 ***************/
@Serializable
data class RiSc4X(
    val schemaVersion: String,
    val title: String,
    val scope: String,
    val valuations: List<RiScValuation>? = null,
    val scenarios: List<RiSc4XScenario>,
)

object RiSc4XScenarioSerializer : FlattenSerializer<RiSc4XScenario>(
    serializer = RiSc4XScenario.generatedSerializer(),
    flattenKey = "scenario",
    subKeys = listOf("ID", "description", "url", "threatActors", "vulnerabilities", "risk", "remainingRisk", "actions"),
)

@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
@Serializable(with = RiSc4XScenarioSerializer::class)
data class RiSc4XScenario(
    val title: String,
    @SerialName("ID")
    val id: String,
    val description: String,
    val url: String? = null,
    val threatActors: List<RiScScenarioThreatActor>,
    val vulnerabilities: List<RiSc4XScenarioVulnerability>,
    val risk: RiScScenarioRisk,
    val remainingRisk: RiScScenarioRisk,
    val actions: List<RiSc4XScenarioAction>,
)

@Serializable
enum class RiSc4XScenarioVulnerability {
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
    EXCESSIVE_USE, ;

    override fun toString(): String = serializer().descriptor.getElementName(ordinal)
}

private object RiSc4XScenarioActionSerializer : FlattenSerializer<RiSc4XScenarioAction>(
    serializer = RiSc4XScenarioAction.generatedSerializer(),
    flattenKey = "action",
    subKeys = listOf("ID", "url", "status", "description"),
)

@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
@Serializable(with = RiSc4XScenarioActionSerializer::class)
data class RiSc4XScenarioAction(
    val title: String,
    @SerialName("ID")
    val id: String,
    val description: String,
    val url: String? = null,
    val status: RiScScenarioActionStatus,
)

/***************
 * VERSION 3.3 *
 ***************/
@Serializable
data class RiSc33(
    val schemaVersion: String,
    val title: String,
    val scope: String,
    val valuations: List<RiScValuation>? = null,
    val scenarios: List<RiSc33Scenario>,
)

object RiSc33ScenarioSerializer : FlattenSerializer<RiSc33Scenario>(
    serializer = RiSc33Scenario.generatedSerializer(),
    flattenKey = "scenario",
    subKeys =
        listOf(
            "ID",
            "description",
            "url",
            "threatActors",
            "vulnerabilities",
            "risk",
            "remainingRisk",
            "actions",
            "existingActions",
        ),
)

@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
@Serializable(with = RiSc33ScenarioSerializer::class)
data class RiSc33Scenario(
    val title: String,
    @SerialName("ID")
    val id: String,
    val description: String,
    val url: String? = null,
    val threatActors: List<RiScScenarioThreatActor>,
    val vulnerabilities: List<RiSc33ScenarioVulnerability>,
    val risk: RiScScenarioRisk,
    val remainingRisk: RiScScenarioRisk,
    val actions: List<RiSc33ScenarioAction>,
    val existingActions: String? = null,
)

@OptIn(SealedSerializationApi::class)
@Serializable
enum class RiSc33ScenarioVulnerability {
    @SerialName("Compromised admin user")
    COMPROMISED_ADMIN_USER,

    @SerialName("Dependency vulnerability")
    DEPENDENCY_VULNERABILITY,

    @SerialName("Disclosed secret")
    DISCLOSED_SECRET,

    @SerialName("Misconfiguration")
    MISCONFIGURATION,

    @SerialName("Input tampering")
    INPUT_TAMPERING,

    @SerialName("User repudiation")
    USER_REPUDIATION,

    @SerialName("Information leak")
    INFORMATION_LEAK,

    @SerialName("Denial of service")
    DENIAL_OF_SERVICE,

    @SerialName("Escalation of rights")
    ESCALATION_OF_RIGHTS, ;

    override fun toString(): String = serializer().descriptor.getElementName(ordinal)
}

private object RiSc33ScenarioActionSerializer : FlattenSerializer<RiSc33ScenarioAction>(
    serializer = RiSc33ScenarioAction.generatedSerializer(),
    flattenKey = "action",
    subKeys = listOf("ID", "url", "status", "description", "owner", "deadline"),
)

@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
@Serializable(with = RiSc33ScenarioActionSerializer::class)
data class RiSc33ScenarioAction(
    val title: String,
    @SerialName("ID")
    val id: String,
    val description: String,
    val url: String? = null,
    val status: RiScScenarioActionStatus,
    val deadline: String? = null,
    val owner: String? = null,
)

/******************************
 * SHARED BETWEEN 3.3 AND 4.X *
 ******************************/

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

@Serializable
data class RiScScenarioRisk(
    val summary: String? = null,
    val probability: Double,
    val consequence: Double,
)
