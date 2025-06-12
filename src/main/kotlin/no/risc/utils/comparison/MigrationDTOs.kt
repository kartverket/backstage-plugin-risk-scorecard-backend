package no.risc.utils.comparison

import kotlinx.serialization.Serializable
import no.risc.risc.models.RiSc3XScenarioVulnerability
import no.risc.risc.models.RiSc4XScenarioVulnerability

// Changes for the migration from version 3.3 to 4.0

@Serializable
data class MigrationChange40(
    val scenarios: List<MigrationChange40Scenario>,
)

@Serializable
data class MigrationChange40Scenario(
    val title: String,
    val id: String,
    val removedExistingActions: String? = null,
    val changedVulnerabilities: List<MigrationChangedTypedValue<RiSc3XScenarioVulnerability, RiSc4XScenarioVulnerability>>,
    val changedActions: List<MigrationChange40Action>,
)

@Serializable
data class MigrationChange40Action(
    val title: String,
    val id: String,
    val removedOwner: String? = null,
    val removedDeadline: String? = null,
)

// Changes for the migration from version 4.0 to 4.1

@Serializable
data class MigrationChange41(
    val scenarios: List<MigrationChange41Scenario>,
)

@Serializable
data class MigrationChange41Scenario(
    val title: String,
    val id: String,
    var changedRiskProbability: MigrationChangedValue<Double>? = null,
    var changedRiskConsequence: MigrationChangedValue<Double>? = null,
    var changedRemainingRiskProbability: MigrationChangedValue<Double>? = null,
    var changedRemainingRiskConsequence: MigrationChangedValue<Double>? = null,
) {
    fun hasChanges() =
        changedRiskConsequence !== null ||
            changedRiskProbability !== null ||
            changedRemainingRiskConsequence != null ||
            changedRemainingRiskProbability != null
}

// General change object
@Serializable
data class MigrationChangedValue<T>(
    val oldValue: T,
    val newValue: T,
)

@Serializable
data class MigrationChangedTypedValue<S, T>(
    val oldValue: S,
    val newValue: T,
)
