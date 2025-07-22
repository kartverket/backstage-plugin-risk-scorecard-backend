package no.risc.utils.comparison

import kotlinx.serialization.Serializable
import no.risc.risc.models.RiSc3XScenarioVulnerability
import no.risc.risc.models.RiSc4XScenarioVulnerability
import no.risc.risc.models.RiScScenarioActionStatus
import no.risc.utils.KNullableOffsetDateTimeSerializer
import java.time.OffsetDateTime

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

// Changes for the migration from version 4.1 to 4.2
@Serializable
data class MigrationChange42(
    val scenarios: List<MigrationChange42Scenario>,
)

@Serializable
data class MigrationChange42Scenario(
    val title: String,
    val id: String,
    val changedActions: List<MigrationChange42Action>,
) {
    fun hasChanges() = changedActions.isNotEmpty()
}

@Serializable
data class MigrationChange42Action(
    val title: String,
    val id: String,
    @Serializable(with = KNullableOffsetDateTimeSerializer::class)
    val lastUpdated: OffsetDateTime? = null,
)

// Changes for the migration from version 4.2 to 5.0
@Serializable
data class MigrationChange50(
    val scenarios: List<MigrationChange50Scenario>,
)

@Serializable
data class MigrationChange50Scenario(
    val title: String,
    val id: String,
    val changedActions: List<MigrationChange50Action>,
) {
    fun hasChanges() = changedActions.isNotEmpty()
}

@Serializable
data class MigrationChange50Action(
    val title: String,
    val id: String,
    val newStatus: RiScScenarioActionStatus,
)

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
