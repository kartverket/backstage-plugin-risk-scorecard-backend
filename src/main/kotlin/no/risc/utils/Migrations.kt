package no.risc.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.risc.risc.models.RiSc33
import no.risc.risc.models.RiSc33Scenario
import no.risc.risc.models.RiSc33ScenarioVulnerability
import no.risc.risc.models.RiSc4X
import no.risc.risc.models.RiSc4XScenario
import no.risc.risc.models.RiSc4XScenarioAction
import no.risc.risc.models.RiSc4XScenarioVulnerability
import no.risc.risc.models.RiScContentResultDTO
import no.risc.utils.comparison.MigrationChange40
import no.risc.utils.comparison.MigrationChange40Action
import no.risc.utils.comparison.MigrationChange40Scenario
import no.risc.utils.comparison.MigrationChange41
import no.risc.utils.comparison.MigrationChange41Scenario
import no.risc.utils.comparison.MigrationChangedTypedValue
import no.risc.utils.comparison.MigrationChangedValue

/**
 * Migrates the supplied RiSc from its current version to supplied latest supported version if possible. Migration is
 * performed as a number of steps. The method currently supports the following steps:
 * - 3.2 -> 3.3
 * - 3.3 -> 4.0 (breaking changes)
 * - 4.0 -> 4.1 (changed probability and consequence values to use base number 20)
 */
fun migrate(
    content: RiScContentResultDTO,
    latestSupportedVersion: String,
): RiScContentResultDTO {
    if (content.riScContent == null) return content

    val schemaVersion =
        parseJSONToElement(content.riScContent)
            .jsonObject
            .getOrElse("schemaVersion") {
                // If schemaVersion is not present, we cannot determine which version to migrate from
                return content
            }.jsonPrimitive
            .content

    // Only perform migration if not already at the desired version
    if (schemaVersion == latestSupportedVersion) {
        if (content.migrationStatus.migrationVersions.fromVersion != null) {
            // Set the toVersion to the latestSupportedVersion
            content.migrationStatus.migrationVersions.toVersion = latestSupportedVersion
        }
        return content
    }

    // Set the fromVersion only if it's not already set
    if (content.migrationStatus.migrationVersions.fromVersion == null) {
        content.migrationStatus.migrationVersions.fromVersion = schemaVersion
    }

    val nextVersionObj =
        when (schemaVersion) {
            "3.2" -> migrateTo32To33(content)
            "3.3" -> migrateFrom33To40(content)
            "4.0" -> migrateFrom40To41(content)
            else -> return content
        }

    return migrate(nextVersionObj, latestSupportedVersion)
}

// Update RiSc scenarios from schemaVersion 3.2 to 3.3. This is necessary because 3.3 is backwards compatible,
// and modifications can only be made when the schemaVersion is 3.3.
fun migrateTo32To33(obj: RiScContentResultDTO): RiScContentResultDTO {
    val migratedSchemaVersion = obj.riScContent!!.replace("\"schemaVersion\": \"3.2\"", "\"schemaVersion\": \"3.3\"")
    return obj.copy(riScContent = migratedSchemaVersion)
}

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
fun migrateFrom33To40(obj: RiScContentResultDTO): RiScContentResultDTO {
    val riSc = Json.decodeFromString<RiSc33>(obj.riScContent!!)

    val changedScenarios = mutableListOf<MigrationChange40Scenario>()

    val migratedRiSc =
        RiSc4X(
            schemaVersion = "4.0",
            title = riSc.title,
            scope = riSc.scope,
            valuations = riSc.valuations,
            scenarios = riSc.scenarios.map { scenario -> updateScenarioFrom33To40(scenario, changedScenarios::add) },
        )

    return obj.copy(
        riScContent = serializeJSON(migratedRiSc),
        migrationStatus =
            obj.migrationStatus.copy(
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
    scenario: RiSc33Scenario,
    addChanges: (MigrationChange40Scenario) -> Unit,
): RiSc4XScenario {
    // Vulnerability enum mapping from 3.3 to 4.0
    fun replaceVulnerability(vulnerability: RiSc33ScenarioVulnerability): RiSc4XScenarioVulnerability =
        when (vulnerability) {
            // Changed
            RiSc33ScenarioVulnerability.COMPROMISED_ADMIN_USER -> RiSc4XScenarioVulnerability.UNAUTHORIZED_ACCESS
            RiSc33ScenarioVulnerability.DISCLOSED_SECRET -> RiSc4XScenarioVulnerability.INFORMATION_LEAK
            RiSc33ScenarioVulnerability.DENIAL_OF_SERVICE -> RiSc4XScenarioVulnerability.EXCESSIVE_USE
            RiSc33ScenarioVulnerability.ESCALATION_OF_RIGHTS -> RiSc4XScenarioVulnerability.UNAUTHORIZED_ACCESS
            RiSc33ScenarioVulnerability.USER_REPUDIATION -> RiSc4XScenarioVulnerability.UNMONITORED_USE
            // Remain the same
            RiSc33ScenarioVulnerability.DEPENDENCY_VULNERABILITY -> RiSc4XScenarioVulnerability.DEPENDENCY_VULNERABILITY
            RiSc33ScenarioVulnerability.INFORMATION_LEAK -> RiSc4XScenarioVulnerability.INFORMATION_LEAK
            RiSc33ScenarioVulnerability.INPUT_TAMPERING -> RiSc4XScenarioVulnerability.INPUT_TAMPERING
            RiSc33ScenarioVulnerability.MISCONFIGURATION -> RiSc4XScenarioVulnerability.MISCONFIGURATION
        }

    val changedActions = mutableListOf<MigrationChange40Action>()
    val changedVulnerabilities =
        mutableListOf<MigrationChangedTypedValue<RiSc33ScenarioVulnerability, RiSc4XScenarioVulnerability>>()
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
                        println("$newVulnerability – $oldVulnerability")
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
 *  Migrate RiSc with changes from 4.0 to 4.1
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
fun migrateFrom40To41(obj: RiScContentResultDTO): RiScContentResultDTO {
    val riSc = Json.decodeFromString<RiSc4X>(obj.riScContent!!)

    val changedScenarios = mutableListOf<MigrationChange41Scenario>()
    val migratedRiSc =
        riSc.copy(
            schemaVersion = "4.1",
            scenarios = riSc.scenarios.map { scenario -> updateScenarioFrom40to41(scenario, changedScenarios::add) },
        )

    return obj.copy(
        riScContent = serializeJSON(migratedRiSc),
        migrationStatus =
            obj.migrationStatus.copy(
                migrationChanges = true,
                migrationRequiresNewApproval = true,
                migrationChanges41 = if (changedScenarios.isNotEmpty()) MigrationChange41(scenarios = changedScenarios) else null,
            ),
    )
}
