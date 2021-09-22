package net.theluckycoder.familyphotos.db

import android.net.Uri
import androidx.room.TypeConverter

class Converters {
//    @TypeConverter
//    fun fromTimestamp(value: Long?): Date? = value?.let { Date(it) }
//
//    @TypeConverter
//    fun dateToTimestamp(date: Date?): Long? = date?.time

    @TypeConverter
    fun uriToString(value: Uri?): String? = value?.toString()

    @TypeConverter
    fun stringToUri(value: String?): Uri? = value?.let { Uri.parse(it) }
}
