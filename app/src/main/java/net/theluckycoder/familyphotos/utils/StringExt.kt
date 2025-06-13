package net.theluckycoder.familyphotos.utils

import java.text.Normalizer
import java.util.Locale

private val normalizationRegex = "\\p{InCombiningDiacriticalMarks}+".toRegex()

fun String.normalize(): String {
    val normalized = Normalizer.normalize(this, Normalizer.Form.NFD)
    return normalized.replace(normalizationRegex, "")
}

fun String.normalizeLowerCase(): String =
    this.normalize().lowercase(Locale.getDefault())