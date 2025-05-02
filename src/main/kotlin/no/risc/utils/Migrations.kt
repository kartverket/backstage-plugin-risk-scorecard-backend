package no.risc.utils

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.risc.risc.MigrationStatus
import no.risc.risc.RiScContentResultDTO

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

    // Replace schemaVersion
    jsonObject["schemaVersion"] = JsonPrimitive("4.0")

    // Update scenarios, if any are present
    jsonObject.computeIfPresent("scenarios") { _, scenarios ->
        scenarios
            .jsonArray
            .map { updateScenarioFrom33To40(it.jsonObject) }
            .let(::JsonArray)
    }

    return obj.copy(
        riScContent = serializeJSON(JsonObject(jsonObject)),
        migrationStatus =
            MigrationStatus(
                migrationChanges = true,
                migrationRequiresNewApproval = true,
                migrationVersions = obj.migrationStatus.migrationVersions,
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
private fun updateScenarioFrom33To40(scenario: JsonObject): JsonObject {
    val scenarioObject = scenario.toMutableMap()

    val scenarioDetails = scenarioObject["scenario"]?.jsonObject?.toMutableMap() ?: return scenario

    // Remove "existingActions"
    scenarioDetails.remove("existingActions")

    // Changed vulnerability names from 3.3 to 4.0
    val replacementMap =
        mapOf(
            "User repudiation" to "Unmonitored use",
            "Compromised admin user" to "Unauthorized access",
            "Escalation of rights" to "Unauthorized access",
            "Disclosed secret" to "Information leak",
            "Denial of service" to "Excessive use",
        )

    // Replace values in vulnerabilities array, if any vulnerabilities are present
    scenarioDetails.computeIfPresent("vulnerabilities") { _, vulnerabilitiesArray ->
        vulnerabilitiesArray
            .jsonArray
            .map { it.jsonPrimitive.content }
            .map { replacementMap.getOrDefault(it, it) }
            .distinct()
            .map(::JsonPrimitive)
            .let(::JsonArray)
    }

    // Remove "owner" and "deadline" from actions, if there are any actions
    scenarioDetails.computeIfPresent("actions") { _, actionsArray ->
        actionsArray
            .jsonArray
            .map { it.jsonObject.toMutableMap() }
            .onEach {
                it.computeIfPresent("action") { _, actionObject ->
                    actionObject.jsonObject
                        .filter { (key, _) -> key != "owner" && key != "deadline" }
                        .let(::JsonObject)
                }
            }.map(::JsonObject)
            .let(::JsonArray)
    }

    scenarioObject["scenario"] = JsonObject(scenarioDetails)
    return JsonObject(scenarioObject)
}

// Update RiSc scenarios from schemaVersion 4.0 to 4.1. This is necessary because the values of probability
// and consequence are updated to using base number 20
fun migrateTo40To41(obj: RiScContentResultDTO): RiScContentResultDTO {
    val migratedSchemaVersion = obj.riScContent!!.replace("\"schemaVersion\": \"4.0\"", "\"schemaVersion\": \"4.1\"")
    return obj.copy(riScContent = migratedSchemaVersion)
}
