package no.risc.utils.comparison

import no.risc.exception.exceptions.DifferenceException
import no.risc.risc.models.RiSc
import no.risc.risc.models.RiSc3X
import no.risc.risc.models.RiSc3XScenario
import no.risc.risc.models.RiSc3XScenarioAction
import no.risc.risc.models.RiSc4X
import no.risc.risc.models.RiSc4XScenario
import no.risc.risc.models.RiSc4XScenarioAction
import no.risc.risc.models.RiScScenarioRisk
import no.risc.risc.models.RiScValuation
import no.risc.risc.models.UnknownRiSc
import no.risc.utils.migrate
import kotlin.collections.component1
import kotlin.collections.component2

/**
 * Compares the updated RiSc to the new old RiSc. Currently supported for RiSc versions 3.2 through 4.1. Comparisons are
 * made by first migrating the old RiSc to the version of the updated RiSc, if differing. Then, the migrated version of
 * the old RiSc is compared against the updated RiSc.
 *
 * @param updatedRiSc The newest version of the RiSc.
 * @param oldRiSc The old version to compare against.
 * @throws DifferenceException If the RiSc is of an unsupported version, the old RiSc has a newer version than the
 *                             updated Risc or migration fails.
 */
fun compare(
    updatedRiSc: RiSc,
    oldRiSc: RiSc,
): RiScChange =
    when (updatedRiSc) {
        is RiSc4X -> comparison4X(updatedRiSc, oldRiSc)
        is RiSc3X -> comparison3X(updatedRiSc, oldRiSc)
        is UnknownRiSc -> throw DifferenceException("The version of the RiSc is unknown and not supported for comparison.")
    }

/**
 * Compares an updated RiSc of version 4.X to an old RiSc. The fields `title` and `scope` are only included in the
 * changes if changes have been made to these. For `valuations` and `scenarios` items are only included if they have
 * been added, updated or deleted. Valuations do not have an ID and are therefore considered deleted and then added on
 * any changes. Scenarios are matched on ID.
 *
 * @param updatedRiSc The newest version of the RiSc.
 * @param oldRiSc The old version to compare against.
 * @throws DifferenceException If migration fails.
 */
fun comparison4X(
    updatedRiSc: RiSc4X,
    oldRiSc: RiSc,
): RiSc4XChange {
    val (migratedOldRiSc, migrationStatus) =
        try {
            migrate(riSc = oldRiSc, endVersion = updatedRiSc.schemaVersion)
        } catch (_: IllegalStateException) {
            throw DifferenceException("The comparison failed due to migration failure of the old RiSc.")
        }

    return RiSc4XChange(
        title = changeForNonMandatorySimpleProperty(migratedOldRiSc.title, updatedRiSc.title),
        scope = changeForNonMandatorySimpleProperty(migratedOldRiSc.scope, updatedRiSc.scope),
        valuations =
            compareValuations(
                oldValuations = migratedOldRiSc.valuations ?: emptyList(),
                newValuations = updatedRiSc.valuations ?: emptyList(),
            ),
        scenarios = compareScenarios4X(oldScenarios = migratedOldRiSc.scenarios, newScenarios = updatedRiSc.scenarios),
        migrationChanges = migrationStatus,
    )
}

/**
 * Compares an updated RiSc of version 3.X to an old RiSc. The fields `title` and `scope` are only included in the
 * changes if changes have been made to these. For `valuations` and `scenarios` items are only included if they have
 * been added, updated or deleted. Valuations do not have an ID and are therefore considered deleted and then added on
 * any changes. Scenarios are matched on ID.
 *
 * @param updatedRiSc The newest version of the RiSc.
 * @param oldRiSc The old version to compare against.
 * @throws DifferenceException If migration fails.
 */
fun comparison3X(
    updatedRiSc: RiSc3X,
    oldRiSc: RiSc,
): RiSc3XChange {
    val (migratedOldRiSc, migrationStatus) = migrate(riSc = oldRiSc, endVersion = updatedRiSc.schemaVersion)

    return RiSc3XChange(
        title = changeForMandatorySimpleProperty(migratedOldRiSc.title, updatedRiSc.title),
        scope = changeForMandatorySimpleProperty(migratedOldRiSc.scope, updatedRiSc.scope),
        valuations =
            compareValuations(
                oldValuations = migratedOldRiSc.valuations ?: emptyList(),
                newValuations = updatedRiSc.valuations ?: emptyList(),
            ),
        scenarios = compareScenarios3X(oldScenarios = migratedOldRiSc.scenarios, newScenarios = updatedRiSc.scenarios),
        migrationChanges = migrationStatus,
    )
}

/**
 * Helper function for comparing basic mandatory properties (string, int, etc.) and getting a tracked change for that
 * property.
 *
 * @param oldValue The value in the old version of the RiSc.
 * @param newValue The value in the new version of the RiSc.
 * @return `ChangedProperty` on changes and `UnchangedProperty` otherwise.
 */
fun <T> changeForMandatorySimpleProperty(
    oldValue: T,
    newValue: T,
): SimpleTrackedProperty<T> = if (oldValue != newValue) ChangedProperty(oldValue, newValue) else UnchangedProperty(newValue)

/**
 * Helper function for comparing basic non-mandatory properties (string, int, etc.) and getting a tracked change for that
 * property. If no change has occurred, then null is returned.
 *
 * @param oldValue The value in the old version of the RiSc.
 * @param newValue The value in the new version of the RiSc.
 */
fun <T> changeForNonMandatorySimpleProperty(
    oldValue: T,
    newValue: T,
): SimpleTrackedProperty<T>? = if (oldValue != newValue) ChangedProperty(oldValue, newValue) else null

/**
 * Helper function for comparing lists of basic properties (string, int, etc.) and getting tracked changes for values
 * that have been added or deleted from the list.
 *
 * @param oldValues The list of values in the list in the old version of the RiSc.
 * @param newValues The list of values in the list in the new version of the RiSc.
 * @return A list of `AddedProperty` and `DeletedProperty`.
 */
fun <T> changeForListOfSimpleProperty(
    oldValues: List<T>,
    newValues: List<T>,
): List<SimpleTrackedProperty<T>> =
    listOf(
        // Deleted Valuations
        *oldValues
            .filter { it !in newValues }
            .map { DeletedProperty<T, T>(it) }
            .toTypedArray(),
        // Added valuations
        *newValues
            .filter { it !in oldValues }
            .map { AddedProperty<T, T>(it) }
            .toTypedArray(),
    )

/**
 * Helper function for comparing lists of complex properties (ones with change tracking within themselves).
 *
 * @param oldValues The list of values in the list in the old version of the RiSc.
 * @param newValues The list of values in the list in the new version of the RiSc.
 * @param keySelector Computes the key to determine which objects are the same, e.g., the ID field for scenarios.
 * @param changeMapper A method for computing the changes between an old and new version of the same object (based on
 *                     key) in the list.
 */
fun <S, T, U> changeForListOfComplexProperty(
    oldValues: List<T>,
    newValues: List<T>,
    keySelector: (T) -> U,
    changeMapper: (T, T) -> S,
): List<TrackedProperty<S, T>> {
    val oldKeys = oldValues.map(keySelector).toSet()
    val newKeys = newValues.map(keySelector).toSet()

    val deletedValues = oldValues.filter { keySelector(it) !in newKeys }.map { DeletedProperty<S, T>(it) }
    val addedValues = newValues.filter { keySelector(it) !in oldKeys }.map { AddedProperty<S, T>(it) }
    val changedValues =
        oldValues
            .filter { keySelector(it) in newKeys }
            // Associate based on keys
            .associateWith { oldValue -> newValues.first { keySelector(it) == keySelector(oldValue) } }
            // Ignore objects that have not changed
            .filter { (oldValue, newValue) -> oldValue != newValue }
            // Map objects based on the change mapper
            .map { (oldValue, newValue) -> ContentChangedProperty<S, T>(changeMapper(oldValue, newValue)) }

    return listOf(
        *deletedValues.toTypedArray(),
        *changedValues.toTypedArray(),
        *addedValues.toTypedArray(),
    )
}

/**
 * Compares and tracks changes for valuations for versions 3.X and 4.X. As valuations do not have IDs in these versions,
 * a valuation is considered deleted and readded if any of its properties are changed.
 *
 * @param oldValuations Valuations in the old version of the RiSc.
 * @param newValuations Valuations in the new version of the RiSc.
 */
fun compareValuations(
    oldValuations: List<RiScValuation>,
    newValuations: List<RiScValuation>,
): List<SimpleTrackedProperty<RiScValuation>> = changeForListOfSimpleProperty(oldValuations, newValuations)

/**
 * Compares and tracks changes for scenarios for versions 4.X. Only scenarios with changes are included. The IDs are
 * used for determining if a change is an addition, a change of an existing scenario or a deletion. A changed scenario
 * always includes the `title`, `id`, `description`, `risk` and `remainingRisk` fields, even if these have not been
 * changed. The remaining fields are only included when changes have been made to them.
 *
 * @param oldScenarios Scenarios in the old version of the RiSc.
 * @param newScenarios Scenarios in the new version of the RiSc.
 */
fun compareScenarios4X(
    oldScenarios: List<RiSc4XScenario>,
    newScenarios: List<RiSc4XScenario>,
): List<TrackedProperty<RiSc4XScenarioChange, RiSc4XScenario>> =
    changeForListOfComplexProperty(
        oldValues = oldScenarios,
        newValues = newScenarios,
        keySelector = { scenario -> scenario.id },
        changeMapper = { oldScenario, newScenario ->
            RiSc4XScenarioChange(
                title = changeForMandatorySimpleProperty(oldScenario.title, newScenario.title),
                id = newScenario.id,
                description = changeForMandatorySimpleProperty(oldScenario.description, newScenario.description),
                url = changeForNonMandatorySimpleProperty(oldScenario.url, newScenario.url),
                threatActors = changeForListOfSimpleProperty(oldScenario.threatActors, newScenario.threatActors),
                vulnerabilities =
                    changeForListOfSimpleProperty(
                        oldScenario.vulnerabilities,
                        newScenario.vulnerabilities,
                    ),
                risk = compareRisk(oldRisk = oldScenario.risk, newRisk = newScenario.risk),
                remainingRisk = compareRisk(oldRisk = oldScenario.remainingRisk, newRisk = newScenario.remainingRisk),
                actions = compareActions4X(oldActions = oldScenario.actions, newActions = newScenario.actions),
            )
        },
    )

/**
 * Compares and tracks changes for actions for version 4.X. Only actions with changes are included. The IDs are used for
 * determining if a change is an addition, a change of an existing action or a deletion. A changed action always
 * includes the `title` and `id` fields. The remaining fields are only included if they have been changed.
 *
 * @param oldActions The list of actions in the old version of the RiSc.
 * @param newActions The list of actions in the new version of the RiSc.
 */
fun compareActions4X(
    oldActions: List<RiSc4XScenarioAction>,
    newActions: List<RiSc4XScenarioAction>,
): List<TrackedProperty<RiSc4XScenarioActionChange, RiSc4XScenarioAction>> =
    changeForListOfComplexProperty(
        oldValues = oldActions,
        newValues = newActions,
        keySelector = { action -> action.id },
        changeMapper = { oldAction, newAction ->
            RiSc4XScenarioActionChange(
                title = changeForMandatorySimpleProperty(oldAction.title, newAction.title),
                id = newAction.id,
                description = changeForMandatorySimpleProperty(oldAction.description, newAction.description),
                url = changeForNonMandatorySimpleProperty(oldAction.url, newAction.url),
                status = changeForNonMandatorySimpleProperty(oldAction.status, newAction.status),
            )
        },
    )

/**
 * Compares and tracks changes for risk objects for versions 3.X and 4.X. The `probability` and `consequence` fields are
 * always included. The `summary` field is only included if changes have been made.
 *
 * @param oldRisk The risk object in the old version of the RiSc.
 * @param newRisk The risk object in the new version of the RiSc.
 */
fun compareRisk(
    oldRisk: RiScScenarioRisk,
    newRisk: RiScScenarioRisk,
): SimpleTrackedProperty<RiScScenarioRiskChange> =
    ContentChangedProperty(
        RiScScenarioRiskChange(
            summary = changeForNonMandatorySimpleProperty(oldValue = oldRisk.summary, newValue = newRisk.summary),
            probability =
                changeForMandatorySimpleProperty(oldValue = oldRisk.probability, newValue = newRisk.probability),
            consequence =
                changeForMandatorySimpleProperty(oldValue = oldRisk.consequence, newValue = newRisk.consequence),
        ),
    )

/**
 * Compares and tracks changes for scenarios for versions 3.X. Only scenarios with changes are included. The IDs are
 * used for determining if a change is an addition, a change of an existing scenario or a deletion. A changed scenario
 * always includes the `title`, `id`, `description`, `risk` and `remainingRisk` fields, even if these have not been
 * changed. The remaining fields are only included when changes have been made to them.
 *
 * @param oldScenarios Scenarios in the old version of the RiSc.
 * @param newScenarios Scenarios in the new version of the RiSc.
 */
fun compareScenarios3X(
    oldScenarios: List<RiSc3XScenario>,
    newScenarios: List<RiSc3XScenario>,
): List<TrackedProperty<RiSc3XScenarioChange, RiSc3XScenario>> =
    changeForListOfComplexProperty(
        oldValues = oldScenarios,
        newValues = newScenarios,
        keySelector = { scenario -> scenario.id },
        changeMapper = { oldScenario, newScenario ->
            RiSc3XScenarioChange(
                title = changeForMandatorySimpleProperty(oldScenario.title, newScenario.title),
                id = newScenario.id,
                description = changeForMandatorySimpleProperty(oldScenario.description, newScenario.description),
                url = changeForNonMandatorySimpleProperty(oldScenario.url, newScenario.url),
                threatActors = changeForListOfSimpleProperty(oldScenario.threatActors, newScenario.threatActors),
                vulnerabilities =
                    changeForListOfSimpleProperty(
                        oldScenario.vulnerabilities,
                        newScenario.vulnerabilities,
                    ),
                risk = compareRisk(oldRisk = oldScenario.risk, newRisk = newScenario.risk),
                remainingRisk = compareRisk(oldRisk = oldScenario.remainingRisk, newRisk = newScenario.remainingRisk),
                actions = compareActions3X(oldActions = oldScenario.actions, newActions = newScenario.actions),
                existingActions =
                    changeForNonMandatorySimpleProperty(
                        oldScenario.existingActions,
                        newScenario.existingActions,
                    ),
            )
        },
    )

/**
 * Compares and tracks changes for actions for version 3.X. Only actions with changes are included. The IDs are used for
 * determining if a change is an addition, a change of an existing action or a deletion. A changed action always
 * includes the `title` and `id` fields. The remaining fields are only included if they have been changed.
 *
 * @param oldActions The list of actions in the old version of the RiSc.
 * @param newActions The list of actions in the new version of the RiSc.
 */
fun compareActions3X(
    oldActions: List<RiSc3XScenarioAction>,
    newActions: List<RiSc3XScenarioAction>,
): List<TrackedProperty<RiSc3XScenarioActionChange, RiSc3XScenarioAction>> =
    changeForListOfComplexProperty(
        oldValues = oldActions,
        newValues = newActions,
        keySelector = { action -> action.id },
        changeMapper = { oldAction, newAction ->
            RiSc3XScenarioActionChange(
                title = changeForMandatorySimpleProperty(oldAction.title, newAction.title),
                id = newAction.id,
                description = changeForMandatorySimpleProperty(oldAction.description, newAction.description),
                url = changeForNonMandatorySimpleProperty(oldAction.url, newAction.url),
                status = changeForNonMandatorySimpleProperty(oldAction.status, newAction.status),
                deadline = changeForNonMandatorySimpleProperty(oldAction.deadline, newAction.deadline),
                owner = changeForNonMandatorySimpleProperty(oldAction.owner, newAction.owner),
            )
        },
    )
