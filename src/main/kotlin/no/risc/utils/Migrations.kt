package no.risc.utils

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import no.risc.risc.MigrationStatus
import no.risc.risc.RiScContentResultDTO

fun migrate(
    obj: RiScContentResultDTO,
    latestSupportedVersion: String,
): RiScContentResultDTO {
    if (obj.riScContent == null) {
        return obj
    }

    val content = obj.riScContent

    val json = Json { ignoreUnknownKeys = true }
    val jsonObject = json.parseToJsonElement(content).jsonObject.toMutableMap()

    val schemaVersion = jsonObject["schemaVersion"]?.jsonPrimitive?.content

    if (schemaVersion == null || schemaVersion == latestSupportedVersion) {
        return obj
    }

    val nextVersionObj =
        when (schemaVersion) {
            "3.2" -> migrateTo32To33(obj)
            "3.3" -> migrateFrom33To40(obj)
            else -> return obj
        }

    return migrate(nextVersionObj, latestSupportedVersion)
}

// Update RiSc scenarios from schemaVersion 3.2 to 3.3. This is necessary because 3.3 is backwards compatible,
// and modifications can only be made when the schemaVersion is 3.3.
fun migrateTo32To33(obj: RiScContentResultDTO): RiScContentResultDTO {
    if (obj.riScContent == null) {
        return obj
    }

    val migratedSchemaVersion = obj.riScContent.replace("\"schemaVersion\": \"3.2\"", "\"schemaVersion\": \"3.3\"")
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
@OptIn(ExperimentalSerializationApi::class)
fun migrateFrom33To40(obj: RiScContentResultDTO): RiScContentResultDTO {
    if (obj.riScContent == null) {
        return obj
    }

    var content = obj.riScContent

    val json = Json { ignoreUnknownKeys = true }
    val jsonObject = json.parseToJsonElement(content).jsonObject.toMutableMap()

    // Replace schemaVersion
    jsonObject["schemaVersion"] = JsonPrimitive("4.0")

    // Update scenarios
    val scenarios = jsonObject["scenarios"]?.jsonArray ?: JsonArray(emptyList())
    val updatedScenarios =
        scenarios.map { scenario ->
            val scenarioObject = scenario.jsonObject.toMutableMap()

            // Remove "existingActions"
            val scenarioDetails = scenarioObject["scenario"]?.jsonObject?.toMutableMap()
            scenarioDetails?.remove("existingActions")

            // Replace values in vulnerabilities array
            val vulnerabilitiesArray = scenarioDetails?.get("vulnerabilities")?.jsonArray?.toMutableList()
            if (vulnerabilitiesArray != null) {
                val replacementMap =
                    mapOf(
                        "User repudiation" to "Unmonitored use",
                        "Compromised admin user" to "Unauthorized access",
                        "Escalation of rights" to "Unauthorized access",
                        "Disclosed secret" to "Information leak",
                        "Denial of service" to "Excessive use",
                    )

                val newVulnerabilities =
                    vulnerabilitiesArray
                        .map { it.jsonPrimitive.content }
                        .toMutableSet()

                replacementMap.forEach { (oldValue, newValue) ->
                    if (newVulnerabilities.contains(oldValue)) {
                        newVulnerabilities.remove(oldValue)
                        newVulnerabilities.add(newValue)
                    }
                }

                // Convert back to JsonArray
                val updatedVulnerabilitiesArray = JsonArray(newVulnerabilities.map { JsonPrimitive(it) })
                scenarioDetails["vulnerabilities"] = updatedVulnerabilitiesArray
            }

            // Remove "owner" and "deadline" from actions
            val actionsArray = scenarioDetails?.get("actions")?.jsonArray?.toMutableList()
            actionsArray?.forEachIndexed { index, actionElement ->
                val actionObject = actionElement.jsonObject.toMutableMap()
                actionObject["action"]?.jsonObject?.toMutableMap()?.apply {
                    this.remove("owner")
                    this.remove("deadline")
                }?.let { updatedAction ->
                    actionObject["action"] = JsonObject(updatedAction)
                }
                actionsArray[index] = JsonObject(actionObject)
            }
            actionsArray?.let { scenarioDetails["actions"] = JsonArray(it) }

            scenarioDetails?.let { scenarioObject["scenario"] = JsonObject(it) }
            JsonObject(scenarioObject)
        }

    jsonObject["scenarios"] = JsonArray(updatedScenarios)

    // Convert the updated JSON object back to string with pretty printing
    val prettyJson =
        Json {
            prettyPrint = true
            prettyPrintIndent = "    "
        }
    content = prettyJson.encodeToString(JsonObject(jsonObject))

    return obj.copy(
        riScContent = content,
        migrationStatus =
            MigrationStatus(
                migrationChanges = true,
                migrationRequiresNewApproval = true,
            ),
    )
}
