package org.njarasoa.fijerena.core.network.provider

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.fixtures.FakeSharedPreferences
import org.njarasoa.fijerena.core.network.profile.ProfileEntity

/** Category filters per (provider, profile) — see docs/plans/archive/20260930_profile-scoped-settings-plan.md. */
class CategoryFiltersStoreTest {
    private lateinit var store: CategoryFiltersStore

    private val adult = CategoryFilters(rules = listOf(CategoryMatcher("Adult")))
    private val sports = CategoryFilters(mode = FilterMode.INCLUDE, rules = listOf(CategoryMatcher("Sport", MatchType.CONTAINS)))

    @Before
    fun setup() {
        val context = mockk<Context>()
        every { context.getSharedPreferences("category_filters", any()) } returns FakeSharedPreferences()
        store = CategoryFiltersStore(context)
    }

    @Test
    fun `each profile has its own filters on the same provider`() {
        store.set(1L, DEFAULT, adult)
        store.set(1L, KID, sports)

        assertEquals(adult, store.get(1L, DEFAULT))
        assertEquals(sports, store.get(1L, KID))
    }

    @Test
    fun `nothing stored falls back to what the caller passes`() {
        assertEquals(CategoryFilters(), store.get(1L, DEFAULT))
        assertEquals(adult, store.get(1L, DEFAULT, fallback = adult))
    }

    @Test
    fun `a stored empty set wins over the fallback`() {
        store.set(1L, KID, CategoryFilters())

        assertEquals(CategoryFilters(), store.get(1L, KID, fallback = adult))
    }

    @Test
    fun `a new profile copies the creator's filters on every provider`() {
        store.set(1L, DEFAULT, adult)
        store.set(2L, DEFAULT, sports)
        store.set(1L, KID, sports)

        store.copyProfile(DEFAULT, NEW)

        assertEquals(adult, store.get(1L, NEW))
        assertEquals(sports, store.get(2L, NEW))
    }

    @Test
    fun `copying a provider brings every profile's filters and drops the target's own`() {
        store.set(1L, DEFAULT, adult)
        store.set(1L, KID, sports)
        store.set(2L, NEW, adult)

        store.copyProvider(1L, 2L)

        assertEquals(adult, store.get(2L, DEFAULT))
        assertEquals(sports, store.get(2L, KID))
        assertFalse(store.has(2L, NEW))
    }

    @Test
    fun `removing a provider or a profile touches only theirs`() {
        store.set(1L, DEFAULT, adult)
        store.set(1L, KID, sports)
        store.set(12L, KID, sports)
        store.set(2L, DEFAULT, adult)

        store.removeProvider(1L)
        assertFalse(store.has(1L, DEFAULT))
        assertFalse(store.has(1L, KID))
        assertEquals(sports, store.get(12L, KID))

        store.removeProfile(KID)
        assertFalse(store.has(12L, KID))
        assertEquals(adult, store.get(2L, DEFAULT))
    }

    @Test
    fun `legacy prefixes shape still decodes`() {
        val context = mockk<Context>()
        val prefs = FakeSharedPreferences()
        every { context.getSharedPreferences("category_filters", any()) } returns prefs
        prefs.edit().putString("1_$DEFAULT", """{"prefixes":["Adult"]}""").commit()

        assertEquals(adult, CategoryFiltersStore(context).get(1L, DEFAULT))
    }

    private companion object {
        const val DEFAULT = ProfileEntity.DEFAULT_ID
        const val KID = "0b7c1d2e-kid"
        const val NEW = "9f8e7d6c-new"
    }
}
