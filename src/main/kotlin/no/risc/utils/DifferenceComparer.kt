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
     * @param map is a Map made from a json-object
     * @return A List<String> with each key in a RiSc and its value.
     */
    fun flatten(map: Map<String, Any?>): List<String> {
        fun returnString(
            key: String,
            value: String,
        ): String {
            return "/$key: $value"
        }

        return map.flatMap { entry ->

            if (entry == null) {
                listOf("${entry.key}")
            }

            when (val value = entry.value) {
                is List<*> -> {
                    val list = jsonParseToElement(value.toString()).jsonArray

                    if (list.size == 0) {
                        listOf(returnString(entry.key, value.toString()))
                    } else {
                        val newMap = value.indices.associate { "${entry.key}/$it" to list[it] }
                        flatten(newMap)
                    }
                }

                is JsonElement -> {
                    val json = jsonParseToElement(value.toString())
                    when (json) {
                        is JsonObject -> {
                            if (json.keys.isEmpty()) {
                                listOf(returnString(entry.key, value.toString()))
                            } else {
                                json.flatMap { flatten(mapOf("${entry.key}/${it.key}" to it.value)) }
                            }
                        }
                        else -> listOf(returnString(entry.key, value.toString()))
                    }
                }
                else -> listOf("${entry.key} - $value is Unknown")
            }
        }
    }

    fun jsonParseToElement(jsonString: String): JsonElement {
        val json = Json { ignoreUnknownKeys = true }
        return json.parseToJsonElement(jsonString)
    }
}

class DifferenceException(message: String) : Exception(message)

@Throws(DifferenceException::class)
fun diff(
    base: String,
    head: String,
): Difference {
    val json = Json { ignoreUnknownKeys = true }
    try {
        val baseJsonObject = json.parseToJsonElement(base).jsonObject.toMap()
        val headJsonObject = json.parseToJsonElement(head).jsonObject.toMap()

        val result1 = FlatMapRiScUtil.flatten(baseJsonObject).associate { it.split(": ").first() to it.split(": ").last() }
        val result2 = FlatMapRiScUtil.flatten(headJsonObject).associate { it.split(": ").first() to it.split(": ").last() }

        val difference = Maps.difference(result1, result2)

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
