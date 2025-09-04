package no.risc.utils

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.jsonObject
import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Date

class KOffsetDateTimeSerializer : KSerializer<OffsetDateTime> {
    private val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("OffsetDateTime", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: OffsetDateTime,
    ) = encoder.encodeString(formatter.format(value))

    override fun deserialize(decoder: Decoder): OffsetDateTime = OffsetDateTime.parse(decoder.decodeString())
}

class KNullableOffsetDateTimeSerializer : KSerializer<OffsetDateTime?> {
    private val delegate = KOffsetDateTimeSerializer()

    override val descriptor: SerialDescriptor = delegate.descriptor.nullable

    override fun serialize(
        encoder: Encoder,
        value: OffsetDateTime?,
    ) {
        if (value == null) {
            encoder.encodeNull()
        } else {
            delegate.serialize(encoder, value)
        }
    }

    override fun deserialize(decoder: Decoder): OffsetDateTime? {
        try {
            if (decoder.decodeNotNullMark()) {
                return delegate.deserialize(decoder)
            } else {
                return decoder.decodeNull()
            }
        } catch (e: DateTimeParseException) {
            return null
        }
    }
}

class KDateSerializer : KSerializer<Date> {
    private val formatter = SimpleDateFormat()
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Date", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: Date,
    ) = encoder.encodeString(formatter.format(value))

    override fun deserialize(decoder: Decoder): Date = formatter.parse(decoder.decodeString())
}

/**
 * A serializer for flattening the JSON based on a single key (`flattenKey`) to potentially multiple keys (`subKeys`) in
 * the class T. For example, if the JSON looks like
 * `{ "name": "Ola Nordmann", "extraInformation": { "age": 38, "occupation": "Developer" } }`
 * we might want to have a class that looks like
 * `data class Person(val name: String, val age: Int, val occupation: String)`.
 * without modifying the JSON schema. This serializer allows for this by marking `Person` with `@KeepGeneratedSerializer`
 * and `@Serializable(with = PersonSerializer::class)`, where we have
 * `object PersonSerializer : FlattenSerializer<Person>(Person.generatedSerializer(), "extraInformation", listOf("age", "occupation"))`.
 *
 * @param serializer A serializer for the class T
 * @param flattenKey The key to flatten
 * @param subKeys The keys that belong to the flattened key
 */
open class FlattenSerializer<T>(
    serializer: KSerializer<T>,
    val flattenKey: String,
    val subKeys: List<String>,
) : JsonTransformingSerializer<T>(serializer) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        val jsonObject = element.jsonObject
        return JsonObject(
            jsonObject
                .toMutableMap()
                .minus(flattenKey)
                .plus(jsonObject[flattenKey]!!.jsonObject.filterKeys { it in subKeys }),
        )
    }

    override fun transformSerialize(element: JsonElement): JsonElement {
        val jsonObject = element.jsonObject
        val subObject = JsonObject(jsonObject.filterKeys { it in subKeys })
        return JsonObject(jsonObject.toMutableMap().minus(subKeys).plus(flattenKey to subObject))
    }
}
