package org.njarasoa.fijerena.testing

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.runBlocking
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.profile.ProfileEntity
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexDatabase

/**
 * Fixed data for the Settings focus tests, written to the real stores (preferences, the settings
 * database) before `setContent`: three profiles and two Xtream sources. The sources' addresses
 * end in `.invalid`, and nothing here starts a sync, so no test reaches the network.
 */
object SettingsFixture {
    const val ADMIN = "admin"
    const val GUEST = "guest"
    const val KID = "kid"

    const val SOURCE_ONE = "source-one"
    const val SOURCE_TWO = "source-two"

    /**
     * Admin (Expert, no PIN), Guest (a grown-up, not Expert) and Kid (a child profile), in that
     * order; Admin and Guest have both sources, Kid has the first. [active] is the profile in use.
     */
    fun seed(
        context: Context,
        active: String,
    ) = runBlocking {
        useFrameworkSqliteForTheGuideIndex(context)
        val database = SettingsDatabase.getInstance(context)
        val profiles = database.profileDao()
        profiles.delete(ProfileEntity.DEFAULT_ID)
        profiles.insert(ProfileEntity(ADMIN, "Admin", createdAt = 1, colorIndex = 0))
        profiles.insert(ProfileEntity(GUEST, "Guest", createdAt = 2, colorIndex = 1))
        profiles.insert(ProfileEntity(KID, "Kid", createdAt = 3, colorIndex = 2))
        val providers = database.providerDao()
        providers.insertProvider(xtream("Source One", SOURCE_ONE))
        providers.insertProvider(xtream("Source Two", SOURCE_TWO))

        val settings = AppSettings(context)
        settings.activeProfileId = active
        settings.setDevMode(ADMIN, true)
        settings.setChildProfile(KID, true)
        settings.setProfileSourceKeys(ADMIN, setOf(SOURCE_ONE, SOURCE_TWO))
        settings.setProfileSourceKeys(GUEST, setOf(SOURCE_ONE, SOURCE_TWO))
        settings.setProfileSourceKeys(KID, setOf(SOURCE_ONE))
    }

    /**
     * Closes and forgets the singletons the screen reaches: Robolectric gives each test a new
     * application and data folder but keeps the classes, so an instance left from the last test
     * would read its files and preferences. A renamed field fails here, not as a stray focus miss.
     */
    fun reset() {
        listOf(
            "org.njarasoa.fijerena.core.network.provider.SettingsDatabase" to "INSTANCE",
            "org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase" to "INSTANCE",
            "org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexDatabase" to "INSTANCE",
            "org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexer" to "instance",
            "org.njarasoa.fijerena.core.network.xmltv.EpgFileManager" to "instance",
            "org.njarasoa.fijerena.core.network.tmdb.TmdbApiService" to "instance",
            "org.njarasoa.fijerena.core.ui.di.AppContainer" to "instance",
        ).forEach { (className, fieldName) ->
            val field = Class.forName(className).getDeclaredField(fieldName)
            field.isAccessible = true
            (field.get(null) as? RoomDatabase)?.close()
            field.set(null, null)
        }
    }

    /**
     * The guide index opens through a native SQLite library (requery) that the JVM doesn't have,
     * and the screen's view models read it on open: an empty in-memory copy on Android's own
     * SQLite takes its place.
     */
    private fun useFrameworkSqliteForTheGuideIndex(context: Context) {
        val field = EpgIndexDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, Room.inMemoryDatabaseBuilder(context, EpgIndexDatabase::class.java).build())
    }

    private fun xtream(
        name: String,
        key: String,
    ) = ProviderEntity(
        name = name,
        url = "http://$key.invalid",
        username = "user",
        type = "XTREAM",
        providerKey = key,
    )
}
