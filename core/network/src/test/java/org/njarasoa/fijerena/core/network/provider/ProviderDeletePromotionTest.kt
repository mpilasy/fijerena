package org.njarasoa.fijerena.core.network.provider

import android.content.Context
import android.content.SharedPreferences
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.fixtures.FakeSharedPreferences
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexDatabase
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase

/**
 * Deleting the active provider moves the device to the first remaining one, whether the user
 * deleted it here or another device did — only the UI path used to, so a device whose provider was
 * deleted elsewhere was left on "No provider set". See
 * docs/plans/archive/20261002_next-level-rock-solid-resilience-plan.md → R-06 step 3.
 */
class ProviderDeletePromotionTest {
    private val prefsFiles = mutableMapOf<String, SharedPreferences>()
    private val context = mockk<Context>(relaxed = true)
    private val dao = mockk<ProviderDao>(relaxed = true)
    private val settingsDb = mockk<SettingsDatabase>(relaxed = true)

    private val active = ProviderEntity(id = 1, name = "A", url = "http://a.test", username = "me", isActive = true, providerKey = "a")
    private val other = ProviderEntity(id = 2, name = "B", url = "http://b.test", username = "me", providerKey = "b")
    private val third = ProviderEntity(id = 3, name = "C", url = "http://c.test", username = "me", providerKey = "c")

    @Before
    fun setup() {
        every { context.getSharedPreferences(any(), any()) } answers { prefsFiles.getOrPut(firstArg()) { FakeSharedPreferences() } }
        every { settingsDb.providerDao() } returns dao
        mockkObject(SettingsDatabase.Companion, XtreamDatabase.Companion, EpgIndexDatabase.Companion)
        every { SettingsDatabase.getInstance(any()) } returns settingsDb
        every { XtreamDatabase.getInstance(any()) } returns mockk(relaxed = true)
        every { EpgIndexDatabase.getInstance(any()) } returns mockk(relaxed = true)
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { any<RoomDatabase>().withTransaction(any<suspend () -> Any?>()) } coAnswers {
            @Suppress("UNCHECKED_CAST")
            (args[1] as suspend () -> Any?).invoke()
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `deleting the active provider activates the first remaining one, in one transaction`() =
        runBlocking {
            coEvery { dao.getProviderById(1) } returns active
            coEvery { dao.getAllProvidersList() } returns listOf(other, third)

            ProviderRepository(context).deleteProvider(1)

            coVerifyOrder {
                dao.deleteProviderRecordingTombstone(active)
                settingsDb.withTransaction(any<suspend () -> Any?>())
                dao.deactivateAll()
                dao.activateProvider(2, any())
            }
        }

    @Test
    fun `a deletion received from another device promotes too`() =
        runBlocking {
            coEvery { dao.getProviderById(1) } returns active
            coEvery { dao.getAllProvidersList() } returns listOf(third)

            ProviderRepository(context).deleteProvider(1, fromRemote = true)

            coVerify { dao.activateProvider(3, any()) }
        }

    @Test
    fun `deleting an inactive provider leaves the active one alone`() =
        runBlocking {
            coEvery { dao.getProviderById(2) } returns other
            coEvery { dao.getAllProvidersList() } returns listOf(active, third)

            ProviderRepository(context).deleteProvider(2)

            coVerify(exactly = 0) { dao.deactivateAll() }
            coVerify(exactly = 0) { dao.activateProvider(any(), any()) }
        }

    @Test
    fun `deleting the last provider activates nothing`() =
        runBlocking {
            coEvery { dao.getProviderById(1) } returns active
            coEvery { dao.getAllProvidersList() } returns emptyList()

            ProviderRepository(context).deleteProvider(1)

            coVerify(exactly = 0) { dao.activateProvider(any(), any()) }
        }
}
