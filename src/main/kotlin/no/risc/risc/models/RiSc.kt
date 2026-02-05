package no.risc.risc.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KeepGeneratedSerializer
import kotlinx.serialization.SealedSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.risc.utils.FlattenSerializer
import no.risc.utils.KNullableOffsetDateTimeSerializer
import no.risc.utils.parseJSONToClass
import no.risc.utils.parseJSONToElement
import no.risc.utils.serializeJSON
import java.time.OffsetDateTime

sealed interface RiSc {
    // Every RiSc should have a schema version (or null if the version is unknown)
    val schemaVersion: RiScVersion?

    companion object {
        /**
         * Creates a RiSc object from the content of a RiSc. If the content is `null` or of an unknown RiSc version,
         * a `UnknownRiSc` object is returned.
         *
         * @param content The content of the RiSc.
         */
        fun fromContent(content: String?): RiSc {
            if (content == null) return UnknownRiSc(content = null)

            val schemaVersion =
                RiScVersion.fromString(
                    parseJSONToElement(content)
                        .jsonObject
                        .getOrElse("schemaVersion") {
                            // If schemaVersion is not present, we cannot determine the version used
                            return UnknownRiSc(content = null)
                        }.jsonPrimitive
                        .content,
                )

            return try {
                when (schemaVersion) {
                    RiScVersion.RiSc3XVersion.VERSION_3_2, RiScVersion.RiSc3XVersion.VERSION_3_3 -> {
                        parseJSONToClass<RiSc3X>(content)
                    }

                    RiScVersion.RiSc4XVersion.VERSION_4_0, RiScVersion.RiSc4XVersion.VERSION_4_1, RiScVersion.RiSc4XVersion.VERSION_4_2 -> {
                        parseJSONToClass<RiSc4X>(content)
                    }

                    RiScVersion.RiSc5XVersion.VERSION_5_0, RiScVersion.RiSc5XVersion.VERSION_5_1,

                    RiScVersion.RiSc5XVersion.VERSION_5_2, RiScVersion.RiSc5XVersion.VERSION_5_3,
                    -> {
                        parseJSONToClass<RiSc5X>(content)
                    }

                    null -> {
                        UnknownRiSc(content = content)
                    }
                }
            } catch (_: IllegalArgumentException) {
                // If parsing fails with an IllegalArgumentException, the riSc is not valid according to the schema.
                UnknownRiSc(content = content)
            }
        }
    }

    /**
     * Converts the RiSc object to a JSON string.
     */
    fun toJSON(): String
}

@Serializable
sealed interface RiScVersion {
    @Serializable
    enum class RiSc5XVersion : RiScVersion {
        @SerialName("5.0")
        VERSION_5_0,

        @SerialName("5.1")
        VERSION_5_1,

        @SerialName("5.2")
        VERSION_5_2,

        @SerialName("5.3")
        VERSION_5_3,
        ;

        override fun asString(): String = serializer().descriptor.getElementName(ordinal)
    }

    @Serializable
    enum class RiSc4XVersion : RiScVersion {
        @SerialName("4.0")
        VERSION_4_0,

        @SerialName("4.1")
        VERSION_4_1,

        @SerialName("4.2")
        VERSION_4_2,

        ;

        override fun asString(): String = serializer().descriptor.getElementName(ordinal)
    }

    @Serializable
    enum class RiSc3XVersion : RiScVersion {
        @SerialName("3.2")
        VERSION_3_2,

        @SerialName("3.3")
        VERSION_3_3, ;

        override fun asString(): String = serializer().descriptor.getElementName(ordinal)
    }

    /**
     * The version number as a string in the format MAJOR.MINOR
     */
    fun asString(): String

    companion object {
        /**
         * Provides a list of all supported versions.
         */
        fun allVersions(): List<RiScVersion> =
            listOf(
                *RiSc3XVersion.entries.toTypedArray(),
                *RiSc4XVersion.entries.toTypedArray(),
                *RiSc5XVersion.entries.toTypedArray(),
            )

        /**
         * Finds the RiScVersion object that corresponds to the provided string, if any. Otherwise, returns null.
         */
        fun fromString(version: String): RiScVersion? = allVersions().firstOrNull { it.asString() == version }
    }
}

/***************
 * VERSION 5.X *
 ***************/

@Serializable
data class RiSc5X(
    override val schemaVersion: RiScVersion.RiSc5XVersion,
    val title: String,
    val scope: String,
    val valuations: List<RiScValuation>? = null,
    val scenarios: List<RiSc5XScenario>,
    @SerialName("metadata_unencrypted") val metadataUnencrypted: RiSc5XMetadataUnencrypted? = null,
) : RiSc {
    override fun toJSON(): String = serializeJSON(this)
}

@Serializable
data class RiSc5XMetadataUnencrypted(
    val belongsTo: String? = null,
)

object RiSc5XScenarioSerializer : FlattenSerializer<RiSc5XScenario>(
    serializer = RiSc5XScenario.generatedSerializer(),
    flattenKey = "scenario",
    subKeys = listOf("ID", "description", "url", "threatActors", "vulnerabilities", "risk", "remainingRisk", "actions"),
)

@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
@Serializable(with = RiSc5XScenarioSerializer::class)
data class RiSc5XScenario(
    val title: String,
    @SerialName("ID")
    val id: String,
    val description: String,
    val url: String? = null,
    val threatActors: List<RiScScenarioThreatActor>,
    val vulnerabilities: List<RiScScenarioVulnerability>,
    val risk: RiScScenarioRisk,
    val remainingRisk: RiScScenarioRisk,
    val actions: List<RiSc5XScenarioAction>,
)

private object RiSc5XScenarioActionSerializer : FlattenSerializer<RiSc5XScenarioAction>(
    serializer = RiSc5XScenarioAction.generatedSerializer(),
    flattenKey = "action",
    subKeys = listOf("ID", "url", "status", "description", "lastUpdated", "lastUpdatedBy"),
)

@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
@Serializable(with = RiSc5XScenarioActionSerializer::class)
data class RiSc5XScenarioAction(
    val title: String,
    @SerialName("ID")
    val id: String,
    val description: String,
    val url: String? = null,
    val status: RiScScenarioActionStatus,
    @Serializable(KNullableOffsetDateTimeSerializer::class)
    val lastUpdated: OffsetDateTime? = null,
    val lastUpdatedBy: String? = null,
)

/***************
 * VERSION 4.X *
 ***************/
@Serializable
data class RiSc4X(
    override val schemaVersion: RiScVersion.RiSc4XVersion,
    val title: String,
    val scope: String,
    val valuations: List<RiScValuation>? = null,
    val scenarios: List<RiSc4XScenario>,
) : RiSc {
    override fun toJSON(): String = serializeJSON(this)
}

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
    val vulnerabilities: List<RiScScenarioVulnerability>,
    val risk: RiScScenarioRisk,
    val remainingRisk: RiScScenarioRisk,
    val actions: List<RiSc4XScenarioAction>,
)

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
    EXCESSIVE_USE, ;

    override fun toString(): String = serializer().descriptor.getElementName(ordinal)
}

private object RiSc4XScenarioActionSerializer : FlattenSerializer<RiSc4XScenarioAction>(
    serializer = RiSc4XScenarioAction.generatedSerializer(),
    flattenKey = "action",
    subKeys = listOf("ID", "url", "status", "description", "lastUpdated"),
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
    val status: RiSc3X4XScenarioActionStatus,
    @Serializable(KNullableOffsetDateTimeSerializer::class)
    val lastUpdated: OffsetDateTime? = null,
)

/**********************
 * VERSIONS 3.2 & 3.3 *
 **********************/
@Serializable
data class RiSc3X(
    override val schemaVersion: RiScVersion.RiSc3XVersion,
    val title: String,
    val scope: String,
    val valuations: List<RiScValuation>? = null,
    val scenarios: List<RiSc3XScenario>,
) : RiSc {
    override fun toJSON(): String = serializeJSON(this)
}

object RiSc3XScenarioSerializer : FlattenSerializer<RiSc3XScenario>(
    serializer = RiSc3XScenario.generatedSerializer(),
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
@Serializable(with = RiSc3XScenarioSerializer::class)
data class RiSc3XScenario(
    val title: String,
    @SerialName("ID")
    val id: String,
    val description: String,
    val url: String? = null,
    val threatActors: List<RiScScenarioThreatActor>,
    val vulnerabilities: List<RiSc3XScenarioVulnerability>,
    val risk: RiScScenarioRisk,
    val remainingRisk: RiScScenarioRisk,
    val actions: List<RiSc3XScenarioAction>,
    val existingActions: String? = null,
)

@OptIn(SealedSerializationApi::class)
@Serializable
enum class RiSc3XScenarioVulnerability {
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

private object RiSc3XScenarioActionSerializer : FlattenSerializer<RiSc3XScenarioAction>(
    serializer = RiSc3XScenarioAction.generatedSerializer(),
    flattenKey = "action",
    subKeys = listOf("ID", "url", "status", "description", "owner", "deadline"),
)

@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
@Serializable(with = RiSc3XScenarioActionSerializer::class)
data class RiSc3XScenarioAction(
    val title: String,
    @SerialName("ID")
    val id: String,
    val description: String,
    val url: String? = null,
    val status: RiSc3X4XScenarioActionStatus,
    val deadline: String? = null,
    val owner: String? = null,
)

/*************************
 * VERSIONS PRIOR TO 3.2 *
 *************************/

data class UnknownRiSc(
    val content: String?,
) : RiSc {
    override val schemaVersion: RiScVersion? get() = null

    override fun toJSON(): String = throw NotImplementedError("The unknown RiSc should never be serialised as is.")
}

/******************************
 * SHARED BETWEEN 3.X AND 4.X *
 ******************************/

@Serializable
@Deprecated("Valuations are no longer used in RiSc 5.2 and later.")
data class RiScValuation(
    val description: String,
    val confidentiality: RiScValuationConfidentiality,
    val integrity: RiScValuationIntegrity,
    val availability: RiScValuationAvailability,
)

@Serializable
@Deprecated("Valuations are no longer used in RiSc 5.2 and later.")
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
@Deprecated("Valuations are no longer used in RiSc 5.2 and later.")
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
@Deprecated("Valuations are no longer used in RiSc 5.2 and later.")
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
enum class RiSc3X4XScenarioActionStatus {
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
enum class RiScScenarioActionStatus {
    @SerialName("OK")
    OK,

    @SerialName("Not OK")
    NOT_OK,

    @SerialName("Not relevant")
    NOT_RELEVANT,
}

@Serializable
data class RiScScenarioRisk(
    val summary: String? = null,
    val probability: Double,
    val consequence: Double,
)
