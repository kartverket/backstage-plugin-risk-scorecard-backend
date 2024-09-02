package no.risc.utils

import com.google.common.collect.Maps
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

@Serializable
data class Difference(
    val entriesOnLeft: List<String> = listOf(),
    val entriesOnRight: List<String> = listOf(),
    val difference: List<String> = listOf(),
)

/**
 * Utility class for flattening a RiSc json-object.
 */
object FlatMapRiScUtil {
    /**
     * Tries to flatten a RiSc map made from json-parsing.
     *
     * Example:
     * {
     *   "firstKey": "hello",
     *   "secondKey": {
     *     "thirdKey": [
     *       "world",
     *       "and",
     *       "others"],
     *       "forthKey": 4
     *   }
     * }
     *
     * should result in the following list:
     *
     * [
     *   "/firstKey: "hello""
     *   "/secondKey/thirdKey/0: "world""
     *   "/secondKey/thirdKey/1: "and""
     *   "/secondKey/thirdKey/2: "others""
     *   "/secondKey/forthKey: 4"
     * ]
     *
     * @param map is a Map made from a json-object
     * @return A List<String> with each key in a RiSc and its value.
     */
    fun flatten(map: Map<String, Any?>): List<String> {
        // Gives us a string in the format of "{key}: {value}"
        fun returnString(
            key: String,
            value: String,
        ): String {
            return "/$key: $value"
        }

        return map.flatMap { entry ->

            when (val value = entry.value) {
                is List<*> -> {
                    // When we encounter a List<*> we know that the value comes from a parsed JsonElement and is therefore a JsonArray.
                    val list = jsonParseToElement(value.toString()).jsonArray

                    // We should show empty lists
                    if (list.size == 0) {
                        listOf(returnString(entry.key, value.toString()))
                    } else {
                        // Convert the list to a Map<String, Any?> and recursively call flatten
                        val newMap = value.indices.associate { "${entry.key}/$it" to list[it] }
                        flatten(newMap)
                    }
                }

                is JsonElement -> {
                    // When we encounter a JsonElement, we want to try and parse it.
                    val json = jsonParseToElement(value.toString())
                    when (json) {
                        is JsonObject -> {
                            // then, if the element is another JsonObject we want to flatten its content.
                            if (json.keys.isEmpty()) {
                                // Unless it is empty, there is no content to flatten.
                                listOf(returnString(entry.key, value.toString()))
                            } else {
                                // Runs flatten recursively on JsonObjects content.
                                json.flatMap { flatten(mapOf("${entry.key}/${it.key}" to it.value)) }
                            }
                        }
                        // If the element is a normal value, stop the recursion.
                        else -> listOf(returnString(entry.key, value.toString()))
                    }
                }
                // Failsafe for unhandled value types.
                else -> listOf("${entry.key} - $value is Unknown")
            }
        }
    }

    fun jsonParseToElement(jsonString: String): JsonElement {
        val json = Json { ignoreUnknownKeys = true }
        return json.parseToJsonElement(jsonString)
    }
}

private fun List<String>.toDifferenceMap(): Map<String, Any> {
    return this.associate { it.split(": ").first() to it.split(": ").last() }
}

class DifferenceException(message: String) : Exception(message)

@Throws(DifferenceException::class)
fun diff(
    base: String,
    head: String,
): Difference {
    val json = Json { ignoreUnknownKeys = true }
    try {
        // Parse JsonObjects from riscs and transfrom to maps.
        val baseJsonObject = json.parseToJsonElement(base).jsonObject.toMap()
        val headJsonObject = json.parseToJsonElement(head).jsonObject.toMap()

        // Flatten out the structures and transform to maps.
        val result1 = FlatMapRiScUtil.flatten(baseJsonObject).toDifferenceMap()
        val result2 = FlatMapRiScUtil.flatten(headJsonObject).toDifferenceMap()

        // Calculate the difference.
        val difference = Maps.difference(result1, result2)

        // Transform the differences to string lists.
        val entriesDiffering: List<String> = difference.entriesDiffering().entries.map { it.key + ": " + it.value }
        val entriesOnLeft = difference.entriesOnlyOnLeft().entries.map { it.key + ": " + it.value }
        val entriesOnRight = difference.entriesOnlyOnRight().entries.map { it.key + ": " + it.value }

        return Difference(
            entriesOnLeft = entriesOnLeft,
            entriesOnRight = entriesOnRight,
            difference = entriesDiffering,
        )
    } catch (e: Exception) {
        throw DifferenceException("Could not convert either source or target RiSc to JsonObject")
    }
}
