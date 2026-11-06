package net.theluckycoder.familyphotos.core.data.local.db

import android.net.Uri
import androidx.core.net.toUri
import androidx.room.TypeConverter
import net.theluckycoder.familyphotos.core.data.model.PhotoType
import net.theluckycoder.familyphotos.core.data.model.ThumbHashBytes

internal class Converters {
    @TypeConverter
    fun uriToString(value: Uri?): String? = value?.toString()

    @TypeConverter
    fun stringToUri(value: String?): Uri? = value?.toUri()

    @TypeConverter
    fun toPhotoType(value: Int) = enumValues<PhotoType>().find { it.index == value }

    @TypeConverter
    fun fromPhotoType(value: PhotoType) = value.index

    @TypeConverter
    fun thumbHashToBytes(value: ThumbHashBytes?): ByteArray? = value?.bytes

    @TypeConverter
    fun bytesToThumbHash(value: ByteArray?): ThumbHashBytes? = value?.let(::ThumbHashBytes)
}
