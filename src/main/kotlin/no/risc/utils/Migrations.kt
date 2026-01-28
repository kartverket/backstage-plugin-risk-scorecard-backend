package no.risc.utils

import no.risc.risc.models.LastPublished
import no.risc.risc.models.MigrationStatus
import no.risc.risc.models.MigrationVersions
import no.risc.risc.models.RiSc
import no.risc.risc.models.RiSc3X
import no.risc.risc.models.RiSc3X4XScenarioActionStatus
import no.risc.risc.models.RiSc3XScenario
import no.risc.risc.models.RiSc3XScenarioVulnerability
import no.risc.risc.models.RiSc4X
import no.risc.risc.models.RiSc4XScenario
import no.risc.risc.models.RiSc4XScenarioAction
import no.risc.risc.models.RiSc5X
import no.risc.risc.models.RiSc5XBackstageMetadata
import no.risc.risc.models.RiSc5XMetadataUnencrypted
import no.risc.risc.models.RiSc5XScenario
import no.risc.risc.models.RiSc5XScenarioAction
import no.risc.risc.models.RiScScenarioActionStatus
import no.risc.risc.models.RiScScenarioVulnerability
import no.risc.risc.models.RiScVersion
import no.risc.utils.comparison.MigrationChange40
import no.risc.utils.comparison.MigrationChange40Action
import no.risc.utils.comparison.MigrationChange40Scenario
import no.risc.utils.comparison.MigrationChange41
import no.risc.utils.comparison.MigrationChange41Scenario
import no.risc.utils.comparison.MigrationChange42
import no.risc.utils.comparison.MigrationChange42Action
import no.risc.utils.comparison.MigrationChange42Scenario
import no.risc.utils.comparison.MigrationChange50
import no.risc.utils.comparison.MigrationChange50Action
import no.risc.utils.comparison.MigrationChange50Scenario
import no.risc.utils.comparison.MigrationChange51
import no.risc.utils.comparison.MigrationChange51Action
import no.risc.utils.comparison.MigrationChange51Scenario
import no.risc.utils.comparison.MigrationChange52
import no.risc.utils.comparison.MigrationChange53
import no.risc.utils.comparison.MigrationChange53BackstageMetadata
import no.risc.utils.comparison.MigrationChange53MetadataUnencrypted
import no.risc.utils.comparison.MigrationChangedTypedValue
import no.risc.utils.comparison.MigrationChangedValue

/**
 * Migrates the supplied RiSc from its current version to supplied latest supported version if possible. Migration is
 * performed as a number of steps. The method currently supports the following steps:
 * - 3.2 -> 3.3
 * - 3.3 -> 4.0 (breaking changes)
 * - 4.0 -> 4.1 (changed probability and consequence values to use base number 20)
 * - 4.1 -> 4.2 (add lastUpdated field to action)
 * - 4.2 -> 5.0 (change action status values)
 * - 5.0 -> 5.1 (add lastUpdatedBy field to action)
 * - 5.1 -> 5.2 (remove valuations)
 *
 * @param riSc The RiSc to migrate.
 * @param lastPublished The last published version of the RisC to use for migration to 4.2
 * @param endVersion The version to migrate to.
 * @throws IllegalStateException If the RiSc is of an unsupported version, the endVersion is a non-supported version or
 *                               the endVersion is an earlier version than the RiSc version.
 */
fun migrate(
    riSc: RiSc,
    lastPublished: LastPublished?,
    endVersion: String,
): Pair<RiSc, MigrationStatus> {
    val toVersion = RiScVersion.fromString(endVersion)

    if (riSc.schemaVersion == null || toVersion == null) throw IllegalStateException("Unsupported migration")

    return handleMigrate(
        riSc = riSc,
        lastPublished = lastPublished,
        migrationStatus =
            MigrationStatus(
                migrationChanges = false,
                migrationRequiresNewApproval = false,
                migrationVersions =
                    MigrationVersions(
                        fromVersion = riSc.schemaVersion?.asString(),
                        toVersion = toVersion.asString(),
                    ),
            ),
        toVersion = toVersion,
    )
}

/**
 * Migrates the supplied RiSc to the supplied 3.X version.
 *
 * @see no.risc.utils.migrate(RiSc, String)
 */
fun migrate(
    riSc: RiSc,
    lastPublished: LastPublished? = null,
    endVersion: RiScVersion.RiSc3XVersion,
): Pair<RiSc3X, MigrationStatus> {
    val (migratedRiSc, migrationStatus) = migrate(riSc = riSc, lastPublished = lastPublished, endVersion = endVersion.asString())
    if (migratedRiSc !is RiSc3X) throw IllegalStateException("Migration to 3.X version failed")
    return Pair(migratedRiSc, migrationStatus)
}

/**
 * Migrates the supplied RiSc to the supplied 4.X version.
 *
 * @see no.risc.utils.migrate(RiSc, String)
 */
fun migrate(
    riSc: RiSc,
    lastPublished: LastPublished? = null,
    endVersion: RiScVersion.RiSc4XVersion,
): Pair<RiSc4X, MigrationStatus> {
    val (migratedRiSc, migrationStatus) = migrate(riSc = riSc, lastPublished = lastPublished, endVersion = endVersion.asString())
    if (migratedRiSc !is RiSc4X) throw IllegalStateException("Migration to 4.X version failed")
    return Pair(migratedRiSc, migrationStatus)
}

/**
 * Migrates the supplied RiSc to the supplied 5.X version.
 *
 * @see no.risc.utils.migrate(RiSc, String)
 */
fun migrate(
    riSc: RiSc,
    lastPublished: LastPublished? = null,
    endVersion: RiScVersion.RiSc5XVersion,
): Pair<RiSc5X, MigrationStatus> {
    val (migratedRiSc, migrationStatus) = migrate(riSc = riSc, lastPublished = lastPublished, endVersion = endVersion.asString())
    if (migratedRiSc !is RiSc5X) throw IllegalStateException("Migration to 5.X version failed")
    return Pair(migratedRiSc, migrationStatus)
}

/**
 * Migrates the supplied RiSc from its current version to supplied latest supported version if possible. Migration is
 * performed as a number of steps. The method currently supports the following steps:
 * - 3.2 -> 3.3
 * - 3.3 -> 4.0 (breaking changes)
 * - 4.0 -> 4.1 (changed probability and consequence values to use base number 20)
 * - 4.1 -> 4.2 (add lastUpdated field to action)
 * - 4.2 -> 5.0 (change action status values)
 *
 * @param riSc The RiSc to migrate
 * @param migrationStatus The migration status so far
 * @param toVersion The version to migrate to.
 * @throws IllegalStateException If the riSc is of an unsupported version, the to version is a non-supported version or
 *                               the to version is an earlier version than the RiSc version.
 */
private fun handleMigrate(
    riSc: RiSc,
    lastPublished: LastPublished?,
    migrationStatus: MigrationStatus,
    toVersion: RiScVersion,
): Pair<RiSc, MigrationStatus> {
    if (toVersion == riSc.schemaVersion) {
        return riSc to migrationStatus
    }

    val (migratedRiSc, migrationStatus) =
        when {
            riSc is RiSc3X && riSc.schemaVersion == RiScVersion.RiSc3XVersion.VERSION_3_2 ->
                migrateFrom32To33(riSc, migrationStatus)

            riSc is RiSc3X && riSc.schemaVersion == RiScVersion.RiSc3XVersion.VERSION_3_3 ->
                migrateFrom33To40(riSc, migrationStatus)

            riSc is RiSc4X && riSc.schemaVersion == RiScVersion.RiSc4XVersion.VERSION_4_0 ->
                migrateFrom40To41(riSc, migrationStatus)

            riSc is RiSc4X && riSc.schemaVersion == RiScVersion.RiSc4XVersion.VERSION_4_1 ->
                migrateFrom41To42(riSc, lastPublished, migrationStatus)

            riSc is RiSc4X && riSc.schemaVersion == RiScVersion.RiSc4XVersion.VERSION_4_2 ->
                migrateFrom42To50(riSc, migrationStatus)

            riSc is RiSc5X && riSc.schemaVersion == RiScVersion.RiSc5XVersion.VERSION_5_0 ->
                migrateFrom50To51(riSc, migrationStatus)

            riSc is RiSc5X && riSc.schemaVersion == RiScVersion.RiSc5XVersion.VERSION_5_1 ->
                migrateFrom51To52(riSc, migrationStatus)

            riSc is RiSc5X && riSc.schemaVersion == RiScVersion.RiSc5XVersion.VERSION_5_2 ->
                migrateFrom52To53(riSc, migrationStatus)

            else -> throw IllegalStateException("Unsupported migration")
        }
    return handleMigrate(migratedRiSc, lastPublished, migrationStatus, toVersion)
}

// Update RiSc scenarios from schemaVersion 3.2 to 3.3. This is necessary because 3.3 is backwards compatible,
// and modifications can only be made when the schemaVersion is 3.3.
fun migrateFrom32To33(
    riSc: RiSc3X,
    migrationStatus: MigrationStatus,
): Pair<RiSc3X, MigrationStatus> = Pair(riSc.copy(schemaVersion = RiScVersion.RiSc3XVersion.VERSION_3_3), migrationStatus)

/**
 * Update RiSc content from version 3.3 to 4.0. Includes breaking changes.
 *
 * Changes include:
 * - Bump schemaVersion to 4.0
 *
 * Replace values in vulnerabilities:
 * - User repudiation -> Unmonitored use
 * - Compromised admin user -> Unauthorized access
 * - Escalation of rights -> Unauthorized access
 * - Disclosed secret -> Information leak
 * - Denial of service -> Excessive use
 *
 * Remove "owner" and "deadline" from actions
 * Remove "existingActions" from scenarios
 */
fun migrateFrom33To40(
    riSc: RiSc3X,
    migrationStatus: MigrationStatus,
): Pair<RiSc4X, MigrationStatus> {
    val changedScenarios = mutableListOf<MigrationChange40Scenario>()

    return Pair(
        RiSc4X(
            schemaVersion = RiScVersion.RiSc4XVersion.VERSION_4_0,
            title = riSc.title,
            scope = riSc.scope,
            valuations = riSc.valuations,
            scenarios = riSc.scenarios.map { scenario -> updateScenarioFrom33To40(scenario, changedScenarios::add) },
        ),
        migrationStatus.copy(
            migrationChanges = true,
            migrationRequiresNewApproval = true,
            migrationChanges40 = if (changedScenarios.isNotEmpty()) MigrationChange40(scenarios = changedScenarios) else null,
        ),
    )
}

/**
 * Updates a scenario with the changes from 3.3 to 4.0.
 *
 * Replace values in vulnerabilities:
 * - User repudiation -> Unmonitored use
 * - Compromised admin user -> Unauthorized access
 * - Escalation of rights -> Unauthorized access
 * - Disclosed secret -> Information leak
 * - Denial of service -> Excessive use
 *
 * Remove "owner" and "deadline" from actions
 * Remove "existingActions" from scenarios
 *
 */
private fun updateScenarioFrom33To40(
    scenario: RiSc3XScenario,
    addChanges: (MigrationChange40Scenario) -> Unit,
): RiSc4XScenario {
    // Vulnerability enum mapping from 3.3 to 4.0
    fun replaceVulnerability(vulnerability: RiSc3XScenarioVulnerability): RiScScenarioVulnerability =
        when (vulnerability) {
            // Changed
            RiSc3XScenarioVulnerability.COMPROMISED_ADMIN_USER -> RiScScenarioVulnerability.UNAUTHORIZED_ACCESS
            RiSc3XScenarioVulnerability.DISCLOSED_SECRET -> RiScScenarioVulnerability.INFORMATION_LEAK
            RiSc3XScenarioVulnerability.DENIAL_OF_SERVICE -> RiScScenarioVulnerability.EXCESSIVE_USE
            RiSc3XScenarioVulnerability.ESCALATION_OF_RIGHTS -> RiScScenarioVulnerability.UNAUTHORIZED_ACCESS
            RiSc3XScenarioVulnerability.USER_REPUDIATION -> RiScScenarioVulnerability.UNMONITORED_USE
            // Remain the same
            RiSc3XScenarioVulnerability.DEPENDENCY_VULNERABILITY -> RiScScenarioVulnerability.DEPENDENCY_VULNERABILITY
            RiSc3XScenarioVulnerability.INFORMATION_LEAK -> RiScScenarioVulnerability.INFORMATION_LEAK
            RiSc3XScenarioVulnerability.INPUT_TAMPERING -> RiScScenarioVulnerability.INPUT_TAMPERING
            RiSc3XScenarioVulnerability.MISCONFIGURATION -> RiScScenarioVulnerability.MISCONFIGURATION
        }

    val changedActions = mutableListOf<MigrationChange40Action>()
    val changedVulnerabilities =
        mutableListOf<MigrationChangedTypedValue<RiSc3XScenarioVulnerability, RiScScenarioVulnerability>>()
    val removedExistingActions: String? =
        if (scenario.existingActions.isNullOrEmpty()) null else scenario.existingActions

    val migratedScenario =
        RiSc4XScenario(
            title = scenario.title,
            id = scenario.id,
            description = scenario.description,
            url = scenario.url,
            threatActors = scenario.threatActors,
            // Map changed vulnerabilities
            vulnerabilities =
                scenario.vulnerabilities
                    .map { oldVulnerability ->
                        val newVulnerability = replaceVulnerability(oldVulnerability)
                        if (newVulnerability.toString() != oldVulnerability.toString()) {
                            changedVulnerabilities.add(MigrationChangedTypedValue(oldVulnerability, newVulnerability))
                        }
                        newVulnerability
                    }.distinct(),
            risk = scenario.risk,
            remainingRisk = scenario.remainingRisk,
            // Remove owner and deadline field from action
            actions =
                scenario.actions.map { action ->
                    if (!action.owner.isNullOrEmpty() || !action.deadline.isNullOrEmpty()) {
                        changedActions.add(
                            MigrationChange40Action(
                                title = action.title,
                                id = action.id,
                                removedOwner = action.owner,
                                removedDeadline = action.deadline,
                            ),
                        )
                    }
                    RiSc4XScenarioAction(
                        title = action.title,
                        id = action.id,
                        description = action.description,
                        url = action.url,
                        status = action.status,
                    )
                },
        )

    if (removedExistingActions != null || changedActions.isNotEmpty() && changedVulnerabilities.isNotEmpty()) {
        addChanges(
            MigrationChange40Scenario(
                title = scenario.title,
                id = scenario.id,
                removedExistingActions = removedExistingActions,
                changedVulnerabilities = changedVulnerabilities,
                changedActions = changedActions,
            ),
        )
    }

    return migratedScenario
}

/**
 * Update a scenario with changes from 4.0 to 4.1
 *
 *  Changes in consequence (in NOK per incident):
 *  1000            ->      8000 = 20^3
 *  30 000          ->      160 000 = 20^4
 *  1 000 000       ->      32 000 000 = 20^5
 *  30 000 000      ->      64 000 000 = 20^6
 *  1 000 000 000   ->      1 280 000 000 = 20^7
 *
 *  Changes in probability (in incidents per year):
 *  0.01    ->      0.0025 = 20^-2 (every 400 years)
 *  0.1     ->      0.05 = 20^-1 (every 20 years)
 *  1       ->      1 = 20^0 (every year)
 *  50      ->      20 = 20^1 (~ monthly)
 *  300     ->      400 = 20^2 (~ daily)
 *
 */
private fun updateScenarioFrom40to41(
    scenario: RiSc4XScenario,
    addChanges: (MigrationChange41Scenario) -> Unit,
): RiSc4XScenario {
    val consequenceMigrations: Map<Double, Double> =
        mapOf(
            1000.0 to 8000.0,
            30000.0 to 160000.0,
            1000000.0 to 3200000.0,
            30000000.0 to 64000000.0,
            1000000000.0 to 1280000000.0,
        ).withDefault { it }

    val probabilityMigrations: Map<Double, Double> =
        mapOf(
            0.01 to 0.0025,
            0.1 to 0.05,
            1.0 to 1.0,
            50.0 to 20.0,
            300.0 to 400.0,
        ).withDefault { it }

    val migratedScenario =
        scenario.copy(
            risk =
                scenario.risk.copy(
                    consequence = consequenceMigrations.getValue(scenario.risk.consequence),
                    probability = probabilityMigrations.getValue(scenario.risk.probability),
                ),
            remainingRisk =
                scenario.remainingRisk.copy(
                    consequence = consequenceMigrations.getValue(scenario.remainingRisk.consequence),
                    probability = probabilityMigrations.getValue(scenario.remainingRisk.probability),
                ),
        )

    fun changeValue(
        oldValue: Double,
        newValue: Double,
    ): MigrationChangedValue<Double>? = if (oldValue != newValue) MigrationChangedValue(oldValue, newValue) else null

    val changes =
        MigrationChange41Scenario(
            title = scenario.title,
            id = scenario.id,
            changedRiskProbability = changeValue(scenario.risk.probability, migratedScenario.risk.probability),
            changedRiskConsequence = changeValue(scenario.risk.consequence, migratedScenario.risk.consequence),
            changedRemainingRiskProbability =
                changeValue(scenario.remainingRisk.probability, migratedScenario.remainingRisk.probability),
            changedRemainingRiskConsequence =
                changeValue(scenario.remainingRisk.consequence, migratedScenario.remainingRisk.consequence),
        )

    if (changes.hasChanges()) addChanges(changes)

    return migratedScenario
}

/**
// *  Migrate RiSc with changes from 4.0 to 4.1
 *
 *  The preset values for consequence and probability have been changed to use base 20.
 *  Note that arbitrary values are allowed for consequence and probability. We leave arbitrary values as is
 *  and migrate only values equal to the previous preset values.
 *
 *  Changes in consequence (in NOK per incident):
 *  1000            ->      8000 = 20^3
 *  30 000          ->      160 000 = 20^4
 *  1 000 000       ->      32 000 000 = 20^5
 *  30 000 000      ->      64 000 000 = 20^6
 *  1 000 000 000   ->      1 280 000 000 = 20^7
 *
 *  Changes in probability (in incidents per year):
 *  0.01    ->      0.0025 = 20^-2 (every 400 years)
 *  0.1     ->      0.05 = 20^-1 (every 20 years)
 *  1       ->      1 = 20^0 (every year)
 *  50      ->      20 = 20^1 (~ monthly)
 *  300     ->      400 = 20^2 (~ daily)
 *
 * */
fun migrateFrom40To41(
    riSc: RiSc4X,
    migrationStatus: MigrationStatus,
): Pair<RiSc4X, MigrationStatus> {
    val changedScenarios = mutableListOf<MigrationChange41Scenario>()
    return Pair(
        riSc.copy(
            schemaVersion = RiScVersion.RiSc4XVersion.VERSION_4_1,
            scenarios = riSc.scenarios.map { scenario -> updateScenarioFrom40to41(scenario, changedScenarios::add) },
        ),
        migrationStatus.copy(
            migrationChanges = true,
            migrationRequiresNewApproval = true,
            migrationChanges41 = if (changedScenarios.isNotEmpty()) MigrationChange41(scenarios = changedScenarios) else null,
        ),
    )
}

fun updateScenarioFrom41To42(
    scenario: RiSc4XScenario,
    lastPublished: LastPublished?,
    addChanges: (MigrationChange42Scenario) -> Unit,
): RiSc4XScenario {
    val migratedScenario =
        scenario.copy(
            actions =
                scenario.actions.map { action ->
                    action.copy(lastUpdated = lastPublished?.dateTime ?: null)
                },
        )

    val changes =
        MigrationChange42Scenario(
            title = migratedScenario.title,
            id = migratedScenario.id,
            changedActions = migratedScenario.actions.map { MigrationChange42Action(it.title, it.id, it.lastUpdated) },
        )

    if (changes.hasChanges()) addChanges(changes)

    return migratedScenario
}

/**
 *  Migrate RiSc with changes from 4.1 to 4.2
 *
 * Add lastUpdated field to action to keep track when the action was last updated.
 * Set to the last published date for RiSc or null if RiSc is not yet published.
 * */
fun migrateFrom41To42(
    riSc: RiSc4X,
    lastPublished: LastPublished?,
    migrationStatus: MigrationStatus,
): Pair<RiSc4X, MigrationStatus> {
    val changedScenarios = mutableListOf<MigrationChange42Scenario>()

    return Pair(
        riSc.copy(
            schemaVersion = RiScVersion.RiSc4XVersion.VERSION_4_2,
            scenarios =
                riSc.scenarios.map { scenario ->
                    updateScenarioFrom41To42(scenario, lastPublished, changedScenarios::add)
                },
        ),
        migrationStatus.copy(
            migrationChanges42 = if (changedScenarios.isNotEmpty()) MigrationChange42(scenarios = changedScenarios) else null,
        ),
    )
}

fun updateScenarioFrom42To50(
    scenario: RiSc4XScenario,
    addChanges: (MigrationChange50Scenario) -> Unit,
): RiSc5XScenario {
    // Action status enum mapping from 4.2 to 5.0
    fun replaceActionStatus(actionStatus: RiSc3X4XScenarioActionStatus): RiScScenarioActionStatus =
        when (actionStatus) {
            RiSc3X4XScenarioActionStatus.NOT_STARTED,
            RiSc3X4XScenarioActionStatus.IN_PROGRESS,
            RiSc3X4XScenarioActionStatus.ON_HOLD,
            -> RiScScenarioActionStatus.NOT_OK
            RiSc3X4XScenarioActionStatus.COMPLETED -> RiScScenarioActionStatus.OK
            RiSc3X4XScenarioActionStatus.ABORTED -> RiScScenarioActionStatus.NOT_RELEVANT
        }

    val changedActionStatus = mutableListOf<MigrationChangedTypedValue<RiSc3X4XScenarioActionStatus, RiScScenarioActionStatus>>()
    val changedActions = mutableListOf<MigrationChange50Action>()

    val migratedScenario =
        RiSc5XScenario(
            title = scenario.title,
            id = scenario.id,
            description = scenario.description,
            url = scenario.url,
            threatActors = scenario.threatActors,
            vulnerabilities = scenario.vulnerabilities,
            risk = scenario.risk,
            remainingRisk = scenario.remainingRisk,
            actions =
                scenario.actions
                    .map { action ->
                        val newActionStatus = replaceActionStatus(action.status)
                        if (newActionStatus.toString() != action.status.toString()) {
                            changedActionStatus.add(MigrationChangedTypedValue(action.status, newActionStatus))
                            changedActions.add(
                                MigrationChange50Action(
                                    title = action.title,
                                    id = action.id,
                                    changedActionStatus = MigrationChangedTypedValue(action.status, newActionStatus),
                                ),
                            )
                        }
                        RiSc5XScenarioAction(
                            title = action.title,
                            id = action.id,
                            description = action.description,
                            url = action.url,
                            status = newActionStatus,
                            lastUpdated = action.lastUpdated,
                        )
                    },
        )

    val changes =
        MigrationChange50Scenario(
            title = migratedScenario.title,
            id = migratedScenario.id,
            changedActionStatus = changedActionStatus,
            changedActions = changedActions,
        )

    if (changes.hasChanges()) addChanges(changes)

    return migratedScenario
}

/**
 * Migrate RiSc with changes from 4.2 to 5.0
 *
 * Change status of actions from
 *
 * Completed -> Ok
 * Not started, In progress, On hold -> Not ok
 * aborted -> Not relevant
 **/

fun migrateFrom42To50(
    riSc: RiSc4X,
    migrationStatus: MigrationStatus,
): Pair<RiSc5X, MigrationStatus> {
    val changedScenarios = mutableListOf<MigrationChange50Scenario>()

    return Pair(
        RiSc5X(
            schemaVersion = RiScVersion.RiSc5XVersion.VERSION_5_0,
            title = riSc.title,
            scope = riSc.scope,
            valuations = riSc.valuations,
            scenarios =
                riSc.scenarios.map { scenario ->
                    updateScenarioFrom42To50(scenario, changedScenarios::add)
                },
        ),
        migrationStatus.copy(
            migrationChanges = true,
            migrationRequiresNewApproval = true,
            migrationChanges50 =
                if (changedScenarios.isNotEmpty()) MigrationChange50(scenarios = changedScenarios) else null,
        ),
    )
}

fun updateScenarioFrom50To51(
    scenario: RiSc5XScenario,
    addChanges: (MigrationChange51Scenario) -> Unit,
): RiSc5XScenario {
    val migratedScenario =
        scenario.copy(
            actions =
                scenario.actions.map { action ->
                    action.copy(lastUpdatedBy = "")
                },
        )

    val changes =
        MigrationChange51Scenario(
            title = migratedScenario.title,
            id = migratedScenario.id,
            changedActions = migratedScenario.actions.map { MigrationChange51Action(it.title, it.id, it.lastUpdatedBy) },
        )

    if (changes.hasChanges()) addChanges(changes)

    return migratedScenario
}

/**
 *  Migrate RiSc with changes from 5.0 to 5.1
 *
 * Add lastUpdatedBy field to action to keep track who the action was last updated by.
 * The field is initially set to empty
 * */
fun migrateFrom50To51(
    riSc: RiSc5X,
    migrationStatus: MigrationStatus,
): Pair<RiSc5X, MigrationStatus> {
    val changedScenarios = mutableListOf<MigrationChange51Scenario>()

    return Pair(
        riSc.copy(
            schemaVersion = RiScVersion.RiSc5XVersion.VERSION_5_1,
            scenarios =
                riSc.scenarios.map { scenario ->
                    updateScenarioFrom50To51(scenario, changedScenarios::add)
                },
        ),
        migrationStatus.copy(
            migrationChanges = true,
            migrationRequiresNewApproval = true,
            migrationChanges51 = if (changedScenarios.isNotEmpty()) MigrationChange51(scenarios = changedScenarios) else null,
        ),
    )
}

/**
 *  Migrate RiSc with changes from 5.1 to 5.2
 *
 * Remove valuations from the RiSc file.
 */
fun migrateFrom51To52(
    riSc: RiSc5X,
    migrationStatus: MigrationStatus,
): Pair<RiSc5X, MigrationStatus> {
    val removedValuationsCount = riSc.valuations?.size ?: 0

    return Pair(
        riSc.copy(
            schemaVersion = RiScVersion.RiSc5XVersion.VERSION_5_2,
            valuations = null,
        ),
        migrationStatus.copy(
            migrationChanges =
                migrationStatus.migrationChanges || removedValuationsCount > 0,
            migrationRequiresNewApproval = true,
            migrationChanges52 =
                MigrationChange52(
                    removedValuationsCount = removedValuationsCount,
                ),
        ),
    )
}

/**
 *  Migrate RiSc with changes from 5.2 to 5.3
 *
 * Unencrypted metadata is introduced. In the first iteration, the metadata includes
 * an entity reference to the backstage entity the RiSc belongs to.
 *
 * From the backend, the entity reference is set to "" (empty) as it is only available from the frontend.
 * The backend migration is responsible for changing the schema structure to include the entity reference
 * field, while the frontend is responsible for population the field when migrating.
 */
fun migrateFrom52To53(
    riSc: RiSc5X,
    migrationStatus: MigrationStatus,
): Pair<RiSc5X, MigrationStatus> {
    val initialBackstageEntityRef = ""
    return Pair(
        riSc.copy(
            schemaVersion = RiScVersion.RiSc5XVersion.VERSION_5_3,
            metadataUnencrypted =
                RiSc5XMetadataUnencrypted(
                    RiSc5XBackstageMetadata(entityRef = initialBackstageEntityRef),
                ),
        ),
        migrationStatus.copy(
            migrationChanges = true,
            migrationRequiresNewApproval = true,
            migrationChanges53 =
                MigrationChange53(
                    metadataUnencrypted =
                        MigrationChange53MetadataUnencrypted(
                            MigrationChange53BackstageMetadata(initialBackstageEntityRef),
                        ),
                ),
        ),
    )
}
