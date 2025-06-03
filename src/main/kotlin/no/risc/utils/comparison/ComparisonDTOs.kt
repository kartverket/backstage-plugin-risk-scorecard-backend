package no.risc.utils.comparison

import kotlinx.serialization.Serializable
import no.risc.risc.models.RiSc3XScenarioVulnerability
import no.risc.risc.models.RiSc4XScenarioVulnerability
import no.risc.risc.models.RiScScenarioActionStatus
import no.risc.risc.models.RiScScenarioThreatActor
import no.risc.risc.models.RiScValuationAvailability
import no.risc.risc.models.RiScValuationConfidentiality
import no.risc.risc.models.RiScValuationIntegrity

@Serializable
enum class ChangeType {
    ADDED,
    CHANGED,
    CONTENT_CHANGED,
    DELETED,
    UNCHANGED,
}

@Serializable
sealed class TrackedProperty<T>(
    val changeType: ChangeType,
) {
    @Serializable
    data class DeletedProperty<T>(
        val oldValue: T,
    ) : TrackedProperty<T>(ChangeType.DELETED)

    @Serializable
    data class ChangedProperty<T>(
        val oldValue: T,
        val newValue: T,
    ) : TrackedProperty<T>(ChangeType.CHANGED)

    @Serializable
    data class AddedProperty<T>(
        val newValue: T,
    ) : TrackedProperty<T>(ChangeType.ADDED)

    @Serializable
    data class ContentChangedProperty<T>(
        val value: T,
    ) : TrackedProperty<T>(ChangeType.CONTENT_CHANGED)

    @Serializable
    data class UnchangedProperty<T>(
        val value: T,
    ) : TrackedProperty<T>(ChangeType.UNCHANGED)
}

typealias DeletedProperty<T> = TrackedProperty.DeletedProperty<T>
typealias ChangedProperty<T> = TrackedProperty.ChangedProperty<T>
typealias AddedProperty<T> = TrackedProperty.AddedProperty<T>
typealias ContentChangedProperty<T> = TrackedProperty.ContentChangedProperty<T>
typealias UnchangedProperty<T> = TrackedProperty.UnchangedProperty<T>

/***************
 * VERSION 4.X *
 ***************/

@Serializable
data class RiSc4XChange(
    val title: TrackedProperty<String>?,
    val scope: TrackedProperty<String>?,
    val valuations: List<TrackedProperty<RiScValuationChange>>? = null,
    val scenarios: List<TrackedProperty<RiSc4XScenarioChange>>? = null,
)

@Serializable
data class RiSc4XScenarioChange(
    val title: TrackedProperty<String>,
    // The id will never change
    val id: String,
    val description: TrackedProperty<String>,
    val url: TrackedProperty<String?>? = null,
    val threatActors: List<TrackedProperty<RiScScenarioThreatActor>>? = null,
    val vulnerabilities: List<TrackedProperty<RiSc4XScenarioVulnerability>>? = null,
    val risk: TrackedProperty<RiScScenarioRiskChange>? = null,
    val remainingRisk: TrackedProperty<RiScScenarioRiskChange>? = null,
    val actions: List<TrackedProperty<RiSc4XScenarioActionChange>>? = null,
)

@Serializable
data class RiSc4XScenarioActionChange(
    val title: TrackedProperty<String>,
    // The id will never change
    val id: String,
    val description: TrackedProperty<String>,
    val url: TrackedProperty<String?>? = null,
    val status: TrackedProperty<RiScScenarioActionStatus>? = null,
)

/************************
 * VERSIONS 3.2 AND 3.3 *
 ************************/

@Serializable
data class RiSc3XChange(
    val title: TrackedProperty<String>?,
    val scope: TrackedProperty<String>?,
    val valuations: List<TrackedProperty<RiScValuationChange>>? = null,
    val scenarios: List<TrackedProperty<RiSc3XScenarioChange>>? = null,
)

@Serializable
data class RiSc3XScenarioChange(
    val title: TrackedProperty<String>,
    // The id will never change
    val id: String,
    val description: TrackedProperty<String>,
    val url: TrackedProperty<String?>? = null,
    val threatActors: List<TrackedProperty<RiScScenarioThreatActor>>? = null,
    val vulnerabilities: List<TrackedProperty<RiSc3XScenarioVulnerability>>? = null,
    val risk: TrackedProperty<RiScScenarioRiskChange>? = null,
    val remainingRisk: TrackedProperty<RiScScenarioRiskChange>? = null,
    val actions: List<TrackedProperty<RiSc3XScenarioActionChange>>? = null,
    val existingActions: TrackedProperty<String?>? = null,
)

@Serializable
data class RiSc3XScenarioActionChange(
    val title: TrackedProperty<String>,
    // The id will never change
    val id: String,
    val description: TrackedProperty<String>,
    val url: TrackedProperty<String?>? = null,
    val status: TrackedProperty<RiScScenarioActionStatus>? = null,
    val deadline: TrackedProperty<String?>? = null,
    val owner: TrackedProperty<String?>? = null,
)

/******************************
 * SHARED BETWEEN 3.X AND 4.X *
 ******************************/

@Serializable
data class RiScValuationChange(
    val description: TrackedProperty<String>,
    val confidentiality: TrackedProperty<RiScValuationConfidentiality>,
    val integrity: TrackedProperty<RiScValuationIntegrity>,
    val availability: TrackedProperty<RiScValuationAvailability>,
)

@Serializable
data class RiScScenarioRiskChange(
    val summary: TrackedProperty<String?>? = null,
    val probability: TrackedProperty<Double>,
    val consequence: TrackedProperty<Double>,
)
