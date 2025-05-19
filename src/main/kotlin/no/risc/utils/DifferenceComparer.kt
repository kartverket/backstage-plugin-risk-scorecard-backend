package no.risc.utils

import kotlinx.serialization.Serializable
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
        ): String = "/$key: $value"

        return map.flatMap { (key, value) ->
            when (value) {
                is List<*> -> {
                    // When we encounter a List<*> we know that the value comes from a parsed JsonElement and is therefore a JsonArray.
                    val list = parseJSONToElement(value.toString()).jsonArray

                    // We should show empty lists
                    if (list.size == 0) {
                        listOf(returnString(key, value.toString()))
                    } else {
                        // Convert the list to a Map<String, Any?> and recursively call flatten
                        flatten(list.withIndex().associate { "$key/${it.index}" to it.value })
                    }
                }

                is JsonElement -> {
                    // When we encounter a JsonElement, we want to try and parse it.
                    val json = parseJSONToElement(value.toString())
                    if (json is JsonObject && json.keys.isNotEmpty()) {
                        // If the element is a JsonObject and has keys, then it must be handled recursively
                        json.flatMap { flatten(mapOf("$key/${it.key}" to it.value)) }
                    } else {
                        // Otherwise, the element is empty or is a normal value
                        listOf(returnString(key, value.toString()))
                    }
                }
                // Failsafe for unhandled value types.
                else -> listOf("/$key - $value is Unknown")
            }
        }
    }
}

private fun List<String>.toDifferenceMap(): Map<String, Any> = this.associate { it.split(": ").first() to it.split(": ").last() }

class DifferenceException(
    message: String,
) : Exception(message)

/**
 * Computes the difference between the two JSON objects provided.
 *
 * @param base: The JSON object prior to the changes
 * @param head: The JSON object after the changes
 */
@Throws(DifferenceException::class)
fun diff(
    base: String,
    head: String,
): Difference {
    try {
        // Parse JsonObjects from riscs and transfrom to maps.
        val baseJsonObject = parseJSONToElement(base).jsonObject
        val headJsonObject = parseJSONToElement(head).jsonObject

        // Flatten out the structures and transform to maps.
        val result1 = FlatMapRiScUtil.flatten(baseJsonObject).toDifferenceMap()
        val result2 = FlatMapRiScUtil.flatten(headJsonObject).toDifferenceMap()

        val entriesDiffering = mutableListOf<String>()
        val entriesOnLeft = mutableListOf<String>()

        result1.forEach { (key, value) ->
            if (result2.containsKey(key)) {
                if (result2[key] != value) {
                    entriesDiffering.add("$key: ($value, ${result2[key]})")
                }
            } else {
                entriesOnLeft.add("$key: $value")
            }
        }

        val entriesOnRight = result2.filterKeys { !result1.containsKey(it) }.map { "${it.key}: ${it.value}" }

        return Difference(
            entriesOnLeft = entriesOnLeft,
            entriesOnRight = entriesOnRight,
            difference = entriesDiffering,
        )
    } catch (_: Exception) {
        throw DifferenceException("Could not convert either source or target RiSc to JsonObject")
    }
}
