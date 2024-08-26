package no.risc.utils

import com.google.common.collect.MapDifference
import com.google.common.collect.Maps
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.util.AbstractMap.SimpleEntry

@Serializable
data class Difference (
    val entriesOnLeft: List<String> = listOf(),
    val entriesOnRight: List<String> = listOf(),
    val difference: List<String> = listOf(),
)
object FlatMapUtils3 {
    fun typeChecker(map: Map<String, Any?>): List<String> {
        fun returnString(key: String, value: String): String {
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
                        typeChecker(newMap)
                    }

                }

                is JsonElement -> {
                    val json = jsonParseToElement(value.toString())
                    when (json) {
                        is JsonObject -> {
                            if (json.keys.isEmpty()) {
                                listOf(returnString(entry.key, value.toString()))
                            } else {
                                json.flatMap { typeChecker(mapOf("${entry.key}/${it.key}" to it.value)) }
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



fun diff(
    content1: String,
    content2: String,
): Difference {
    val json = Json { ignoreUnknownKeys = true }
    val jsonObject1 = json.parseToJsonElement(content1).jsonObject.toMap()
    val jsonObject2 = json.parseToJsonElement(content2).jsonObject.toMap()

    val result1 = FlatMapUtils3.typeChecker(jsonObject1).associate { it.split(": ").first() to it.split(": ").last() }
    val result2 = FlatMapUtils3.typeChecker(jsonObject2).associate { it.split(": ").first() to it.split(": ").last() }


    val difference = Maps.difference(result1, result2)

    val entriesDiffering: List<String> = difference.entriesDiffering().entries.map { it.key + ": " + it.value }
    val entriesOnLeft = difference.entriesOnlyOnLeft().entries.map { it.key + ": " + it.value }
    val entriesOnRight = difference.entriesOnlyOnRight().entries.map { it.key + ": " + it.value }

    return Difference(
        entriesOnLeft = entriesOnLeft,
        entriesOnRight = entriesOnRight,
        difference = entriesDiffering,
    )

}

