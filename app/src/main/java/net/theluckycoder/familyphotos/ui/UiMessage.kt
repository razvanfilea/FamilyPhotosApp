package net.theluckycoder.familyphotos.ui

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

enum class UiMessageType {
    Success,
    Error,
    Info,
}

sealed interface UiMessage {
    val type: UiMessageType

    data class Text(val value: String, override val type: UiMessageType = UiMessageType.Info) : UiMessage
    data class Resource(
        @StringRes val resId: Int,
        val args: List<Any> = emptyList(),
        override val type: UiMessageType = UiMessageType.Info,
    ) : UiMessage

    data class PluralResource(
        @PluralsRes val resId: Int,
        val quantity: Int,
        val args: List<Any> = emptyList(),
        override val type: UiMessageType = UiMessageType.Info,
    ) : UiMessage
}