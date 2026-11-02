package org.njarasoa.fijerena.core.ui.utils

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

sealed class UiText {
    data class DynamicString(
        val value: String,
    ) : UiText()

    class StringResource(
        @param:StringRes val resId: Int,
        vararg val args: Any,
    ) : UiText()

    /** A count text: the plural form for [count], formatted with [args]. */
    class PluralResource(
        @param:PluralsRes val resId: Int,
        val count: Int,
        vararg val args: Any,
    ) : UiText()

    @Composable
    fun asString(): String =
        when (this) {
            is DynamicString -> value
            is StringResource -> stringResource(resId, *args)
            is PluralResource -> pluralStringResource(resId, count, *args)
        }

    fun asString(context: Context): String =
        when (this) {
            is DynamicString -> value
            is StringResource -> context.getString(resId, *args)
            is PluralResource -> context.resources.getQuantityString(resId, count, *args)
        }
}
