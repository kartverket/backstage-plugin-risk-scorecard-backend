package no.risc.utils
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class KOffsetDateTimeSerializer : KSerializer<OffsetDateTime> {
    private val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("OffsetDateTime", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: OffsetDateTime,
    ) = encoder.encodeString(formatter.format(value))

    override fun deserialize(decoder: Decoder): OffsetDateTime = OffsetDateTime.parse(decoder.decodeString())
}
