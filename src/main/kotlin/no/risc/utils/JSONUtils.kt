package no.risc.utils

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

// Reuse the instance as instantiating the JSON serializer is an expensive call.
val JSONIgnoreParser = Json { ignoreUnknownKeys = true }

@OptIn(ExperimentalSerializationApi::class)
val JSONPrettyPrintFormat =
    Json {
        prettyPrint = true
        prettyPrintIndent = "    "
    }

/**
 * Parses the JSON string to a JsonElement, ignoring unknown keys.
 */
fun parseJSONToElement(jsonString: String): JsonElement = JSONIgnoreParser.parseToJsonElement(jsonString)

/**
 * Serializes the provided JsonElement to a string, using a pretty print format.
 */
fun serializeJSON(jsonElement: JsonElement): String = JSONPrettyPrintFormat.encodeToString(jsonElement)
