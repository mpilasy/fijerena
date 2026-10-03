package org.njarasoa.fijerena.feature.category.components

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import org.njarasoa.fijerena.core.ui.viewmodels.CategoryViewModel

/**
 * The list a Live TV channel was chosen from, carried from browse into the preview and full
 * screen: the preview panel shows it, Up/Down in full screen zap through it, and the panel's
 * tabs switch it explicitly (UX overhaul plan Part II, Live TV flows, target 1 / LT2).
 *
 * [id] doubles as the category id the list is known by ([CategoryViewModel.RECENT_CATEGORY_ID],
 * [CategoryViewModel.FAVORITES_CATEGORY_ID] or the real category), which is what `StreamList`
 * keys its per-list state and row actions on.
 */
internal sealed interface ChannelContext {
    val id: String

    /** A real category of the source: the one browsed into when the channel was picked. */
    data class Category(
        override val id: String,
        val name: String,
    ) : ChannelContext

    data object Recent : ChannelContext {
        override val id: String = CategoryViewModel.RECENT_CATEGORY_ID
    }

    data object Favorites : ChannelContext {
        override val id: String = CategoryViewModel.FAVORITES_CATEGORY_ID
    }

    companion object {
        /** For `rememberSaveable`: a `Category` saves its id and name, the two virtual lists their id. */
        val Saver: Saver<ChannelContext, Any> =
            listSaver(
                save = { context ->
                    when (context) {
                        is Category -> listOf(context.id, context.name)
                        else -> listOf(context.id)
                    }
                },
                restore = { saved ->
                    when (saved[0]) {
                        CategoryViewModel.RECENT_CATEGORY_ID -> Recent
                        CategoryViewModel.FAVORITES_CATEGORY_ID -> Favorites
                        else -> Category(id = saved[0], name = saved.getOrElse(1) { saved[0] })
                    }
                },
            )
    }
}
