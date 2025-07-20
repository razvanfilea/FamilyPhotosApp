package net.theluckycoder.familyphotos.data.local.db

import android.net.Uri
import androidx.core.net.toUri
import androidx.room.TypeConverter
import net.theluckycoder.familyphotos.data.model.PhotoType

class Converters {
    @TypeConverter
    fun uriToString(value: Uri?): String? = value?.toString()

    @TypeConverter
    fun stringToUri(value: String?): Uri? = value?.toUri()
}
