package net.theluckycoder.familyphotos.ui

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SnackbarManager @Inject constructor() {

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 16)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    fun showMessage(text: String, type: UiMessageType = UiMessageType.Info) {
        _messages.tryEmit(UiMessage.Text(text, type))
    }

    fun showMessage(@StringRes resId: Int, type: UiMessageType = UiMessageType.Info, vararg args: Any) {
        _messages.tryEmit(UiMessage.Resource(resId, args.toList(), type))
    }

    fun showPluralMessage(@PluralsRes resId: Int, quantity: Int, type: UiMessageType = UiMessageType.Info, vararg args: Any) {
        _messages.tryEmit(UiMessage.PluralResource(resId, quantity, args.toList(), type))
    }
}
