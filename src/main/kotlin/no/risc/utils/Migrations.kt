package no.risc.utils

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.risc.risc.models.RiScContentResultDTO
import no.risc.utils.comparison.MigrationChange40
import no.risc.utils.comparison.MigrationChange40Action
import no.risc.utils.comparison.MigrationChange40Scenario
import no.risc.utils.comparison.MigrationChange41
import no.risc.utils.comparison.MigrationChange41Scenario
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
    val jsonObject = parseJSONToElement(obj.riScContent!!).jsonObject.toMutableMap()

    val changedScenarios = mutableListOf<MigrationChange40Scenario>()

    // Replace schemaVersion
    jsonObject["schemaVersion"] = JsonPrimitive("4.0")

    // Update scenarios, if any are present
    jsonObject.computeIfPresent("scenarios") { _, scenarios ->
        scenarios
            .jsonArray
            .map {
                val (updatedScenario, scenarioChanges) = updateScenarioFrom33To40(it.jsonObject)
                if (scenarioChanges != null) changedScenarios.add(scenarioChanges)
                updatedScenario
            }.let(::JsonArray)
    }

    return obj.copy(
        riScContent = serializeJSON(JsonObject(jsonObject)),
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
private fun updateScenarioFrom33To40(scenario: JsonObject): Pair<JsonObject, MigrationChange40Scenario?> {
    val scenarioObject = scenario.toMutableMap()

    val scenarioDetails = scenarioObject["scenario"]?.jsonObject?.toMutableMap() ?: return Pair(scenario, null)

    var removedExistingActions: String? = null

    // Remove "existingActions" and keep track of the removed ones
    scenarioDetails.computeIfPresent("existingActions") { _, existingActions ->
        existingActions.jsonPrimitive.content.also { content ->
            if (content.isNotBlank()) removedExistingActions = content
        }
        null
    }

    // Changed vulnerability names from 3.3 to 4.0
    val replacementMap =
        mapOf(
            "User repudiation" to "Unmonitored use",
            "Compromised admin user" to "Unauthorized access",
            "Escalation of rights" to "Unauthorized access",
            "Disclosed secret" to "Information leak",
            "Denial of service" to "Excessive use",
        )

    val changedVulnerabilities = mutableListOf<MigrationChangedValue<String>>()
    // Replace values in vulnerabilities array, if any vulnerabilities are present
    scenarioDetails.computeIfPresent("vulnerabilities") { _, vulnerabilitiesArray ->
        vulnerabilitiesArray
            .jsonArray
            .map { it.jsonPrimitive.content }
            .map { oldValue ->
                replacementMap
                    .getOrDefault(oldValue, oldValue)
                    .also { newValue ->
                        // Keep track of changed vulnerabilities
                        if (oldValue != newValue) changedVulnerabilities.add(MigrationChangedValue(oldValue, newValue))
                    }
            }.distinct()
            .map(::JsonPrimitive)
            .let(::JsonArray)
    }

    val changedActions = mutableListOf<MigrationChange40Action>()
    // Remove "owner" and "deadline" from actions, if there are any actions
    scenarioDetails.computeIfPresent("actions") { _, actionsArray ->
        actionsArray
            .jsonArray
            .map { it.jsonObject.toMutableMap() }
            .onEach {
                it.computeIfPresent("action") { _, actionObject ->
                    // Record the changed action if owner or deadline is present and contains text
                    val owner = actionObject.jsonObject["owner"]?.jsonPrimitive?.content
                    val deadline = actionObject.jsonObject["deadline"]?.jsonPrimitive?.content

                    if (!owner.isNullOrEmpty() || !deadline.isNullOrEmpty()) {
                        changedActions.add(
                            MigrationChange40Action(
                                title = it["title"]!!.jsonPrimitive.content,
                                id = actionObject.jsonObject["ID"]!!.jsonPrimitive.content,
                                removedOwner = owner,
                                removedDeadline = deadline,
                            ),
                        )
                    }

                    actionObject.jsonObject
                        .filterKeys { key -> key != "owner" && key != "deadline" }
                        .let(::JsonObject)
                }
            }.map(::JsonObject)
            .let(::JsonArray)
    }

    scenarioObject["scenario"] = JsonObject(scenarioDetails)

    if (removedExistingActions == null && changedActions.isEmpty() && changedVulnerabilities.isEmpty()) {
        return Pair(JsonObject(scenarioObject), null)
    }

    return Pair(
        JsonObject(scenarioObject),
        MigrationChange40Scenario(
            title = scenarioObject["title"]!!.jsonPrimitive.content,
            id = scenarioDetails["ID"]!!.jsonPrimitive.content,
            removedExistingActions = removedExistingActions,
            changedVulnerabilities = changedVulnerabilities,
            changedActions = changedActions,
        ),
    )
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
private fun updateScenarioFrom40to41(scenario: JsonObject): Pair<JsonObject, MigrationChange41Scenario?> {
    val scenarioObject = scenario.toMutableMap()

    val scenarioDetails = scenarioObject["scenario"]?.jsonObject?.toMutableMap() ?: return Pair(scenario, null)

    val changes =
        MigrationChange41Scenario(
            title = scenarioObject["title"]!!.jsonPrimitive.content,
            id = scenarioDetails["ID"]!!.jsonPrimitive.content,
        )

    val consequenceMigrations: Map<Int, Int> =
        mapOf(
            1000 to 8000,
            30000 to 160000,
            1000000 to 3200000,
            30000000 to 64000000,
            1000000000 to 1280000000,
        )

    val probabilityMigrations: Map<Double, Double> =
        mapOf(
            0.01 to 0.0025,
            0.1 to 0.05,
            1.0 to 1.0,
            50.0 to 20.0,
            300.0 to 400.0,
        )

    fun migrateRiskFrom40to41(
        riskElement: JsonElement,
        probabilityChangeSetter: (values: MigrationChangedValue<Double>) -> Unit,
        consequenceChangeSetter: (values: MigrationChangedValue<Int>) -> Unit,
    ): JsonObject {
        val risk = riskElement.jsonObject.toMutableMap()

        risk["probability"]?.jsonPrimitive?.doubleOrNull?.let { oldValue ->
            val newValue = probabilityMigrations.getOrDefault(oldValue, oldValue)
            if (oldValue != newValue) probabilityChangeSetter(MigrationChangedValue(oldValue, newValue))
            risk["probability"] = JsonPrimitive(newValue)
        }

        risk["consequence"]?.jsonPrimitive?.intOrNull?.let { oldValue ->
            val newValue = consequenceMigrations.getOrDefault(oldValue, oldValue)
            if (oldValue != newValue) consequenceChangeSetter(MigrationChangedValue(oldValue, newValue))
            risk["consequence"] = JsonPrimitive(newValue)
        }

        return JsonObject(risk)
    }

    // Migrate risk
    scenarioDetails.computeIfPresent("risk") { _, riskElement ->
        migrateRiskFrom40to41(
            riskElement = riskElement,
            probabilityChangeSetter = { changes.changedRiskProbability = it },
            consequenceChangeSetter = { changes.changedRiskConsequence = it },
        )
    }

    // Migrate remaining risk
    scenarioDetails.computeIfPresent("remainingRisk") { _, remainingRiskElement ->
        migrateRiskFrom40to41(
            riskElement = remainingRiskElement,
            probabilityChangeSetter = { changes.changedRemainingRiskProbability = it },
            consequenceChangeSetter = { changes.changedRemainingRiskConsequence = it },
        )
    }

    scenarioObject["scenario"] = JsonObject(scenarioDetails)
    return Pair(
        JsonObject(scenarioObject),
        if (changes.hasChanges()) changes else null,
    )
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
    val jsonObject = parseJSONToElement(obj.riScContent!!).jsonObject.toMutableMap()

    // Change schema version 4.0 -> 4.1
    jsonObject["schemaVersion"] = JsonPrimitive("4.1")

    val changedScenarios = mutableListOf<MigrationChange41Scenario>()

    // Migrate consequence and probability in all scenarios
    jsonObject.computeIfPresent("scenarios") { _, scenarios ->
        scenarios
            .jsonArray
            .map {
                val (updatedScenario, scenarioChanges) = updateScenarioFrom40to41(it.jsonObject)
                if (scenarioChanges != null) changedScenarios.add(scenarioChanges)
                updatedScenario
            }.let(::JsonArray)
    }

    return obj.copy(
        riScContent = serializeJSON(JsonObject(jsonObject)),
        migrationStatus =
            obj.migrationStatus.copy(
                migrationChanges = true,
                migrationRequiresNewApproval = true,
                migrationChanges41 = if (changedScenarios.isNotEmpty()) MigrationChange41(scenarios = changedScenarios) else null,
            ),
    )
}
