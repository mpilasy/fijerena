package org.njarasoa.fijerena.core.network.provider

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.profile.ProfileRepository
import org.njarasoa.fijerena.core.network.xtream.db.XtreamCategoryEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase

/**
 * Category filters per profile and provider — see
 * docs/plans/archive/20260930_profile-scoped-settings-plan.md.
 *
 * Runs against this test APK's own databases and prefs, never the app's. Uses profiles it creates
 * itself: another test in this APK deletes `default`.
 */
@RunWith(AndroidJUnit4::class)
class ProfileCategoryFiltersTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val adult = CategoryFilters(rules = listOf(CategoryMatcher("Adult")))

    @Test
    fun upgrade_copiesProviderFiltersToEveryProfile_andEditsStayPerProfile() =
        runBlocking {
            val appSettings = AppSettings(context)
            val parentId = ProfileRepository(context).addProfile("Parent", 1)
            val kidId = ProfileRepository(context).addProfile("Kid", 2)
            appSettings.activeProfileId = parentId
            val providers = ProviderRepository(context)

            // A provider as stored before profiles had filters: filters inside its settings JSON.
            val id = providers.addProvider("xt", "http://xt.test", "u", "p", "XTREAM")
            val dao = SettingsDatabase.getInstance(context).providerDao()
            dao.updateProvider(dao.getProviderById(id)!!.copy(providerSettings = """{"categoryFilters":{"prefixes":["Adult"]}}"""))

            ProviderRepository(context).migrateCategoryFiltersToProfiles()

            assertFalse(dao.getProviderById(id)!!.providerSettings.contains("Adult"))
            assertEquals(adult, ProviderRepository(context).getProviderSettings(id).categoryFilters)
            appSettings.activeProfileId = kidId
            assertEquals(adult, ProviderRepository(context).getProviderSettings(id).categoryFilters)

            // Kid clears theirs; Parent keeps its own.
            val kidSettings = ProviderRepository(context).getProviderSettings(id)
            ProviderRepository(context).updateProviderSettings(id, kidSettings.copy(categoryFilters = CategoryFilters()))
            assertEquals(CategoryFilters(), ProviderRepository(context).getProviderSettings(id).categoryFilters)
            appSettings.activeProfileId = parentId
            assertEquals(adult, ProviderRepository(context).getProviderSettings(id).categoryFilters)
        }

    @Test
    fun switchingProfile_recomputesExcludedFlags() =
        runBlocking {
            val appSettings = AppSettings(context)
            val firstId = ProfileRepository(context).addProfile("First", 1)
            appSettings.activeProfileId = firstId
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

            // A new profile copies the creator's filters; then gets its own.
            val otherId = ProfileRepository(context).addProfile("Other", 3)
            appSettings.activeProfileId = otherId
            assertEquals(adult, providers.getProviderSettings(id).categoryFilters)
            providers.updateProviderSettings(id, providers.getProviderSettings(id).copy(categoryFilters = CategoryFilters()))
            assertEquals(listOf("Adult Movies", "Comedy"), visible())

            // Switching back re-applies the first profile's filters.
            appSettings.activeProfileId = firstId
            providers.applyCategoryFiltersForSwitch(otherId, firstId)
            assertEquals(listOf("Comedy"), visible())
        }
}
