package org.njarasoa.fijerena.core.network.profile

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The nightly TV walk's test household (debug builds only): on a fresh install, one Xtream source,
 * Admin (Expert), Guest and Kid (a child profile), all three with the source; never on an app that
 * has a source already. Against a real in-memory `providers.db`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TestHouseholdTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: SettingsDatabase

    @Before
    fun setup() {
        db =
            Room
                .inMemoryDatabaseBuilder(context, SettingsDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        mockkObject(SettingsDatabase.Companion)
        every { SettingsDatabase.getInstance(any()) } returns db
        // What a fresh install's providers.db starts with.
        runBlocking { db.profileDao().insert(ProfileEntity(ProfileEntity.DEFAULT_ID, ProfileEntity.DEFAULT_NAME, 0L)) }
    }

    @After
    fun tearDown() {
        unmockkAll()
        db.close()
    }

    @Test
    fun `a fresh install gets the source and the three profiles`() =
        runBlocking {
            val providerId = TestHousehold.seed(context, URL, "test", "test")

            val source = db.providerDao().getAllProvidersList().single()
            assertEquals(providerId, source.id)
            assertEquals(URL, source.url)
            assertEquals("XTREAM", source.type)
            val profiles = db.profileDao().getAll()
            assertEquals(listOf("Admin", "Guest", "Kid"), profiles.map { it.name })
            val (admin, guest, kid) = profiles
            assertEquals(ProfileEntity.DEFAULT_ID, admin.id)
            val settings = AppSettings(context)
            assertTrue(settings.devMode(admin.id))
            assertFalse(settings.devMode(guest.id))
            assertFalse(settings.isChildProfile(guest.id))
            assertTrue(settings.isChildProfile(kid.id))
            assertFalse(settings.devMode(kid.id))
            profiles.forEach { assertEquals(it.name, setOf(source.providerKey), settings.profileSourceKeys(it.id)) }
        }

    @Test
    fun `an app with a source already is left as it is`() =
        runBlocking {
            TestHousehold.seed(context, URL, "test", "test")

            assertNull(TestHousehold.seed(context, "http://other:8090", "test", "test"))

            assertEquals(1, db.providerDao().getProviderCount())
            assertEquals(3, db.profileDao().count())
        }

    private companion object {
        const val URL = "http://10.0.2.2:8090"
    }
}
