package net.theluckycoder.familyphotos.core.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.io.encoding.Base64

/**
 * A decoded ThumbHash. Wraps the bytes because arrays compare by identity, which would break
 * data class equality (Paging diffing, Compose skipping) and cache lookups.
 */
@Immutable
@Serializable(with = ThumbHashBytesSerializer::class)
class ThumbHashBytes(val bytes: ByteArray) {

    private val hash = bytes.contentHashCode()

    override fun equals(other: Any?): Boolean =
        this === other || (other is ThumbHashBytes && hash == other.hash && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = hash

    companion object {
        fun fromBase64(value: String?): ThumbHashBytes? {
            if (value.isNullOrEmpty()) return null
            return try {
                ThumbHashBytes(Base64.decode(value))
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}

internal object ThumbHashBytesSerializer : KSerializer<ThumbHashBytes> {
    override val descriptor = PrimitiveSerialDescriptor("ThumbHashBytes", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ThumbHashBytes) =
        encoder.encodeString(Base64.encode(value.bytes))

    // Invalid saved state shouldn't crash; an empty hash just renders no placeholder
    override fun deserialize(decoder: Decoder): ThumbHashBytes =
        ThumbHashBytes.fromBase64(decoder.decodeString()) ?: ThumbHashBytes(ByteArray(0))
}
