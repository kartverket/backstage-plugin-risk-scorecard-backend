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
 * Parses the JSON string to a given class, ignoring unknown keys.
 */
inline fun <reified T> parseJSONToClass(jsonString: String): T = JSONIgnoreParser.decodeFromString<T>(jsonString)

/**
 * Serializes the provided serializable element to a string, using a pretty print format.
 */
inline fun <reified T> serializeJSON(element: T): String = JSONPrettyPrintFormat.encodeToString(element)
