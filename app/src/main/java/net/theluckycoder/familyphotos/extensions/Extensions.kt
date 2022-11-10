package net.theluckycoder.familyphotos.extensions

import android.os.Parcel
import android.os.Parcelable
import kotlinx.parcelize.parcelableCreator

inline fun <reified T : Parcelable> Parcel.readList(): MutableList<T> {
    val list = mutableListOf<T>()
    readTypedList(list, parcelableCreator<T>())
    return list
}
