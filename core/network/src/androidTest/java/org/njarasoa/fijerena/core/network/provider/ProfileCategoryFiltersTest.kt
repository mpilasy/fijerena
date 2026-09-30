package org.njarasoa.fijerena.core.network.provider

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.profile.ProfileEntity
import org.njarasoa.fijerena.core.network.profile.ProfileRepository
import org.njarasoa.fijerena.core.network.xtream.db.XtreamCategoryEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase

/**
 * Category filters per profile and provider — see
 * docs/plans/20260930_profile-scoped-settings-plan.md.
 *
 * Runs against this test APK's own databases and prefs, never the app's.
 */
@RunWith(AndroidJUnit4::class)
class ProfileCategoryFiltersTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val adult = CategoryFilters(rules = listOf(CategoryMatcher("Adult")))

    @Test
    fun upgrade_copiesProviderFiltersToEveryProfile_andEditsStayPerProfile() =
        runBlocking {
            val appSettings = AppSettings(context)
            appSettings.activeProfileId = ProfileEntity.DEFAULT_ID
            val providers = ProviderRepository(context)
            val kidId = ProfileRepository(context).addProfile("Kid", 2)

            // A provider as stored before profiles had filters: filters inside its settings JSON.
            val id = providers.addProvider("xt", "http://xt.test", "u", "p", "XTREAM")
            val dao = SettingsDatabase.getInstance(context).providerDao()
            dao.updateProvider(dao.getProviderById(id)!!.copy(providerSettings = """{"categoryFilters":{"prefixes":["Adult"]}}"""))

            ProviderRepository(context).migrateCategoryFiltersToProfiles()

            assertFalse(dao.getProviderById(id)!!.providerSettings.contains("Adult"))
            assertEquals(adult, ProviderRepository(context).getProviderSettings(id).categoryFilters)
            appSettings.activeProfileId = kidId
            assertEquals(adult, ProviderRepository(context).getProviderSettings(id).categoryFilters)

            // Kid clears theirs; Default keeps its own.
            val kidSettings = ProviderRepository(context).getProviderSettings(id)
            ProviderRepository(context).updateProviderSettings(id, kidSettings.copy(categoryFilters = CategoryFilters()))
            assertEquals(CategoryFilters(), ProviderRepository(context).getProviderSettings(id).categoryFilters)
            appSettings.activeProfileId = ProfileEntity.DEFAULT_ID
            assertEquals(adult, ProviderRepository(context).getProviderSettings(id).categoryFilters)
        }

    @Test
    fun switchingProfile_recomputesExcludedFlags() =
        runBlocking {
            val appSettings = AppSettings(context)
            appSettings.activeProfileId = ProfileEntity.DEFAULT_ID
            val providers = ProviderRepository(context)
            val id = providers.addProvider("xt2", "http://xt2.test", "u", "p", "XTREAM")
            val categoryDao = XtreamDatabase.getInstance(context).categoryDao()
            categoryDao.insertAll(
                listOf(
                    XtreamCategoryEntity("1", id, "Adult Movies", type = XtreamCategoryEntity.TYPE_VOD),
                    XtreamCategoryEntity("2", id, "Comedy", type = XtreamCategoryEntity.TYPE_VOD),
                ),
            )
            providers.updateProviderSettings(id, providers.getProviderSettings(id).copy(categoryFilters = adult))
            fun visible() = categoryDao.getCategories(id, XtreamCategoryEntity.TYPE_VOD).map { it.categoryName }
            assertEquals(listOf("Comedy"), visible())

            // A new profile copies Default's filters; then gets its own.
            val otherId = ProfileRepository(context).addProfile("Other", 3)
            appSettings.activeProfileId = otherId
            providers.updateProviderSettings(id, providers.getProviderSettings(id).copy(categoryFilters = CategoryFilters()))
            assertEquals(listOf("Adult Movies", "Comedy"), visible())

            // Switching back to Default re-applies its filters.
            appSettings.activeProfileId = ProfileEntity.DEFAULT_ID
            providers.applyCategoryFiltersForSwitch(otherId, ProfileEntity.DEFAULT_ID)
            assertEquals(listOf("Comedy"), visible())
        }
}
