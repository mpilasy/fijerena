package org.njarasoa.fijerena.core.player.service

import android.content.Context

/**
 * Applies the in-app language to [StreamingPlaybackService], which builds the player's error
 * messages. Android gives a service the device language; the app's own choice lives in a module
 * this one can't see, so the app sets [wrap] at startup (to the same wrap MainActivity uses).
 * See the playback capability errors plan → P3.
 */
object PlaybackServiceLocale {
    @Volatile
    var wrap: (Context) -> Context = { it }
}
