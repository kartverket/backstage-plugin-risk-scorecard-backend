package no.risc.utils.comparison

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import no.risc.risc.models.MigrationStatus
import no.risc.risc.models.RiSc3X4XScenarioActionStatus
import no.risc.risc.models.RiSc3XScenario
import no.risc.risc.models.RiSc3XScenarioAction
import no.risc.risc.models.RiSc3XScenarioVulnerability
import no.risc.risc.models.RiSc4XScenario
import no.risc.risc.models.RiSc4XScenarioAction
import no.risc.risc.models.RiSc5XScenario
import no.risc.risc.models.RiSc5XScenarioAction
import no.risc.risc.models.RiScScenarioActionStatus
import no.risc.risc.models.RiScScenarioThreatActor
import no.risc.risc.models.RiScScenarioVulnerability
import no.risc.risc.models.RiScValuation
import no.risc.utils.KNullableOffsetDateTimeSerializer
import java.time.OffsetDateTime

/**
 * A tracked property type for handling tracking changes of five types (ADDED, CHANGED, CONTENT_CHANGED, DELETED and UNCHANGED).
 * Uses two types `T` and `K` to allow for two types of change tracking objects:
 * - Type `S` is used for CHANGED and CONTENT_CHANGED
 * - Type `T` is used for ADDED, DELETED and UNCHANGED
 */
@Serializable
sealed interface TrackedProperty<S, T>

/**
 * Indicates that the object of type T previously assigned to the given property has been deleted from the RiSc.
 */
@Serializable
@SerialName("DELETED")
data class DeletedProperty<S, T>(
    val oldValue: T,
) : TrackedProperty<S, T>

/**
 * Indicates that the object of type T previously assigned to the given property has had its value changed. Only used
 * from primitive properties, where the whole object is always exchanged. Such properties are typically strings or
 * numbers.
 */
@Serializable
@SerialName("CHANGED")
data class ChangedProperty<S, T>(
    val oldValue: S?,
    val newValue: S?,
) : TrackedProperty<S, T>

/**
 * Indicates that a new object of type T has been assigned to the given property. A new object refers here either to a
 * value set to an optional property that was previously unset or adding a new object to a list/map.
 */
@Serializable
@SerialName("ADDED")
data class AddedProperty<S, T>(
    val newValue: T,
) : TrackedProperty<S, T>

/**
 * Indicates that the object of type T previously assigned to the given property has had its value changed. Only used
 * for non-primitive properties, where the object consists of multiple separately tracked sub-fields that can be changed
 * independently. Examples include actions and scenarios.
 */
@Serializable
@SerialName("CONTENT_CHANGED")
data class ContentChangedProperty<S, T>(
    val value: S,
) : TrackedProperty<S, T>

/**
 *  Indicates that the object of type T assigned to the given property has not changed. Used for fields that should
 *  always be included in the change set, typically to visualise other changes. Examples include titles and descriptions
 *  of scenarios, which can be used to give the user an indication of which scenario other changes belong to.
 */
@Serializable
@SerialName("UNCHANGED")
data class UnchangedProperty<S, T>(
    val value: T,
) : TrackedProperty<S, T>

/**
 * A simple tracked property where the type of the change tracking object is the same for all types of changes.
 */
typealias SimpleTrackedProperty<S> = TrackedProperty<S, S>

/**
 * Represents the changes made to a RiSc. These changes always include a migration status object for the changes made
 * by migrating the old RiSc version to the version of the new one.
 */
@Serializable
sealed interface RiScChange {
    val migrationChanges: MigrationStatus
}

/***************
 * VERSION 5.X *
 ***************/

@Serializable
@SerialName("5.*")
data class RiSc5XChange(
    val title: SimpleTrackedProperty<String>? = null,
    val scope: SimpleTrackedProperty<String>? = null,
    val valuations: List<SimpleTrackedProperty<RiScValuation>>,
    val scenarios: List<TrackedProperty<RiSc5XScenarioChange, RiSc5XScenario>>,
    override val migrationChanges: MigrationStatus,
) : RiScChange

@Serializable
data class RiSc5XScenarioChange(
    val title: SimpleTrackedProperty<String>,
    val id: String,
    val description: SimpleTrackedProperty<String>,
    val url: SimpleTrackedProperty<String?>? = null,
    val threatActors: List<SimpleTrackedProperty<RiScScenarioThreatActor>>,
    val vulnerabilities: List<SimpleTrackedProperty<RiScScenarioVulnerability>>,
    val risk: SimpleTrackedProperty<RiScScenarioRiskChange>,
    val remainingRisk: SimpleTrackedProperty<RiScScenarioRiskChange>,
    val actions: List<TrackedProperty<RiSc5XScenarioActionChange, RiSc5XScenarioAction>>,
)

@Serializable
data class RiSc5XScenarioActionChange(
    val title: SimpleTrackedProperty<String>,
    val id: String,
    val description: SimpleTrackedProperty<String>,
    val url: SimpleTrackedProperty<String?>? = null,
    val status: SimpleTrackedProperty<RiScScenarioActionStatus>? = null,
    val lastUpdated: SimpleTrackedProperty<
        @Serializable(KNullableOffsetDateTimeSerializer::class)
        OffsetDateTime?,
    >? = null,
    val lastUpdatedBy: SimpleTrackedProperty<String?>? = null,
)

/***************
 * VERSION 4.X *
 ***************/

@Serializable
@SerialName("4.*")
data class RiSc4XChange(
    val title: SimpleTrackedProperty<String>? = null,
    val scope: SimpleTrackedProperty<String>? = null,
    val valuations: List<SimpleTrackedProperty<RiScValuation>>,
    val scenarios: List<TrackedProperty<RiSc4XScenarioChange, RiSc4XScenario>>,
    override val migrationChanges: MigrationStatus,
) : RiScChange

@Serializable
data class RiSc4XScenarioChange(
    val title: SimpleTrackedProperty<String>,
    // The id will never change
    val id: String,
    val description: SimpleTrackedProperty<String>,
    val url: SimpleTrackedProperty<String?>? = null,
    val threatActors: List<SimpleTrackedProperty<RiScScenarioThreatActor>>,
    val vulnerabilities: List<SimpleTrackedProperty<RiScScenarioVulnerability>>,
    val risk: SimpleTrackedProperty<RiScScenarioRiskChange>,
    val remainingRisk: SimpleTrackedProperty<RiScScenarioRiskChange>,
    val actions: List<TrackedProperty<RiSc4XScenarioActionChange, RiSc4XScenarioAction>>,
)

@Serializable
data class RiSc4XScenarioActionChange(
    val title: SimpleTrackedProperty<String>,
    // The id will never change
    val id: String,
    val description: SimpleTrackedProperty<String>,
    val url: SimpleTrackedProperty<String?>? = null,
    val status: SimpleTrackedProperty<RiSc3X4XScenarioActionStatus>? = null,
    val lastUpdated: SimpleTrackedProperty<
        @Serializable(KNullableOffsetDateTimeSerializer::class)
        OffsetDateTime?,
    >? = null,
)

/************************
 * VERSIONS 3.2 AND 3.3 *
 ************************/

@Serializable
@SerialName("3.*")
data class RiSc3XChange(
    val title: SimpleTrackedProperty<String>?,
    val scope: SimpleTrackedProperty<String>?,
    val valuations: List<SimpleTrackedProperty<RiScValuation>>? = null,
    val scenarios: List<TrackedProperty<RiSc3XScenarioChange, RiSc3XScenario>>? = null,
    override val migrationChanges: MigrationStatus,
) : RiScChange

@Serializable
data class RiSc3XScenarioChange(
    val title: SimpleTrackedProperty<String>,
    // The id will never change
    val id: String,
    val description: SimpleTrackedProperty<String>,
    val url: SimpleTrackedProperty<String?>? = null,
    val threatActors: List<SimpleTrackedProperty<RiScScenarioThreatActor>>? = null,
    val vulnerabilities: List<SimpleTrackedProperty<RiSc3XScenarioVulnerability>>? = null,
    val risk: SimpleTrackedProperty<RiScScenarioRiskChange>,
    val remainingRisk: SimpleTrackedProperty<RiScScenarioRiskChange>,
    val actions: List<TrackedProperty<RiSc3XScenarioActionChange, RiSc3XScenarioAction>>? = null,
    val existingActions: SimpleTrackedProperty<String?>? = null,
)

@Serializable
data class RiSc3XScenarioActionChange(
    val title: SimpleTrackedProperty<String>,
    // The id will never change
    val id: String,
    val description: SimpleTrackedProperty<String>,
    val url: SimpleTrackedProperty<String?>? = null,
    val status: SimpleTrackedProperty<RiSc3X4XScenarioActionStatus>? = null,
    val deadline: SimpleTrackedProperty<String?>? = null,
    val owner: SimpleTrackedProperty<String?>? = null,
)

/******************************
 * SHARED BETWEEN 3.X AND 4.X *
 ******************************/

@Serializable
data class RiScScenarioRiskChange(
    val summary: SimpleTrackedProperty<String?>? = null,
    val probability: SimpleTrackedProperty<Double>,
    val consequence: SimpleTrackedProperty<Double>,
)
