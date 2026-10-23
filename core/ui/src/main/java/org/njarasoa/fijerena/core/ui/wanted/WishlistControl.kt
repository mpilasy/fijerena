package org.njarasoa.fijerena.core.ui.wanted

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.wanted.WantedTitles
import org.njarasoa.fijerena.core.ui.utils.launchGuarded

/**
 * A film or show page's Wishlist icon and its ask-once prompt (wishlist UX plan, Phase 1).
 * [isOn]: the title is on the profile's Wishlist; [toggle] adds or removes it. [askRemove]: the
 * title is on the Wishlist, watched, and the prompt was never answered — the page shows it, and
 * either answer goes through [answer] (true: remove it).
 */
class WishlistControl(
    val isOn: Boolean,
    val toggle: () -> Unit,
    val askRemove: Boolean,
    val answer: (remove: Boolean) -> Unit,
)

/**
 * The page's [WishlistControl], or null when the page offers none: no TMDB id, a child profile
 * (the Wishlist is hidden there), or a title that is not on the Wishlist. This page is a source's,
 * so a source has the title: there is nothing to add. A title on the Wishlist keeps its icon until
 * the page is left, so a removal can be undone and TV focus does not lose its button. [title]
 * and [year] stand in when TMDB doesn't answer; [watched] says the film, or an episode of the
 * show, is watched.
 */
@Composable
fun rememberWishlist(
    contentType: String,
    tmdbId: String?,
    title: String,
    year: Int?,
    watched: Boolean,
): WishlistControl? {
    val context = LocalContext.current
    val child =
        remember {
            val settings = AppSettings(context.applicationContext)
            settings.isChildProfile(settings.activeProfileId)
        }
    val wanted = remember { WantedTitles.getInstance(context.applicationContext) }
    val state by wanted.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val id = tmdbId?.takeIf { it.isNotBlank() && it != "0" }
    val entry = id?.let { state.titles.firstOrNull { it.contentType == contentType && it.tmdbId == id } }
    var wasOn by remember(contentType, id) { mutableStateOf(false) }
    if (entry != null) wasOn = true
    return if (id == null || child || !wasOn) {
        null
    } else {
        val key = WantedTitles.Key(contentType, id)
        WishlistControl(
            isOn = entry != null,
            toggle = {
                scope.launchGuarded("Wishlist.toggle") {
                    if (entry != null) wanted.remove(key) else wanted.addFromTmdb(contentType, id, title, year)
                }
            },
            askRemove = entry != null && watched && entry.askedAt == null,
            answer = { remove ->
                scope.launchGuarded("Wishlist.answer") {
                    wanted.markAsked(key)
                    if (remove) wanted.remove(key)
                }
            },
        )
    }
}
