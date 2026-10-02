package org.njarasoa.fijerena.core.network.profile

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase

/**
 * Deleting the `default` profile clears what is its own from the provider-level storage it
 * shares with everyone — Jellyfin logins, Recent Categories, bookmarks, legacy blobs — and leaves
 * what is genuinely shared: other providers' logins and migration flags.
 * See docs/plans/20260929_live-sync-plan.md → User profiles.
 *
 * Runs against this test APK's own databases and prefs, never the app's.
 */
@RunWith(AndroidJUnit4::class)
class DeleteDefaultProfileTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun deletingDefault_clearsItsOwnDataAndKeepsSharedLogins() =
        runBlocking {
            val appSettings = AppSettings(context)
            appSettings.activeProfileId = ProfileEntity.DEFAULT_ID
            val providers = ProviderRepository(context)
            val profiles = ProfileRepository(context)

            val jellyfinId = providers.addProvider("jf", "http://jf.test", "kilonga", "jfpw", "JELLYFIN")
            providers.saveJellyfinSession(jellyfinId, "token", "user-id")
            val xtreamId = providers.addProvider("xt", "http://xt.test", "xuser", "xpw", "XTREAM")
            val mediaCache = context.getSharedPreferences("media_cache_$xtreamId", Context.MODE_PRIVATE)
            mediaCache
                .edit()
                .putString("recent_categories_MOVIES", "[]")
                .putString("last_content_type", "MOVIES")
                .putString("favorites_v2", "[]")
                .putBoolean("watch_state_migrated_v1", true)
                .commit()
            XtreamDatabase.getInstance(context).watchStateDao().upsertProgress(
                providerId = xtreamId,
                profileId = ProfileEntity.DEFAULT_ID,
                itemId = "m1",
                contentType = "MOVIES",
                itemName = "Film",
                categoryId = "c1",
                positionMs = 1_000L,
                durationMs = 5_000L,
                isCompleted = false,
                now = 1L,
                seriesId = null,
                episodeId = null,
                seriesName = null,
                episodeExtension = null,
                audioTrackIndex = null,
                subtitleTrackIndex = null,
            )

            val otherId = profiles.addProfile("Other", 1)
            appSettings.activeProfileId = otherId
            assertEquals(ProfileRepository.DeleteBlocked.NONE, profiles.deleteProfile(ProfileEntity.DEFAULT_ID))

            assertFalse(SettingsDbProbe.profileExists(context, ProfileEntity.DEFAULT_ID))
            // Default's Jellyfin login is gone; the Xtream login everyone shares is not.
            assertEquals("", providers.getProviderById(jellyfinId)?.username)
            assertNull(providers.getPassword(jellyfinId))
            assertEquals("xpw", providers.getPassword(xtreamId))
            // Its Recent Categories, bookmarks and legacy blobs are gone; migration flags stay.
            assertFalse(mediaCache.contains("recent_categories_MOVIES"))
            assertFalse(mediaCache.contains("last_content_type"))
            assertFalse(mediaCache.contains("favorites_v2"))
            assertTrue(mediaCache.getBoolean("watch_state_migrated_v1", false))
            assertTrue(mediaCache.getBoolean("favorites_migrated_v1", false))
            // And its rows.
            assertTrue(
                XtreamDatabase
                    .getInstance(context)
                    .watchStateDao()
                    .getAll(xtreamId, ProfileEntity.DEFAULT_ID)
                    .isEmpty(),
            )

            // Adding a Jellyfin server now stores no login for the profile that no longer exists.
            val laterJellyfinId = providers.addProvider("jf2", "http://jf2.test", "someone", "pw2", "JELLYFIN")
            assertNull(providers.getPassword(laterJellyfinId))
        }
}

private object SettingsDbProbe {
    suspend fun profileExists(
        context: Context,
        id: String,
    ): Boolean =
        org.njarasoa.fijerena.core.network.provider.SettingsDatabase
            .getInstance(context)
            .profileDao()
            .exists(id)
}
