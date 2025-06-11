package net.theluckycoder.familyphotos.db

import android.net.Uri
import androidx.room.TypeConverter
import androidx.core.net.toUri

class Converters {
    @TypeConverter
    fun uriToString(value: Uri?): String? = value?.toString()

    @TypeConverter
    fun stringToUri(value: String?): Uri? = value?.toUri()
}
