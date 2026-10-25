package org.njarasoa.fijerena.core.ui.home

import android.content.Context
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.MediaProvider
import org.njarasoa.fijerena.core.player.domain.ProviderCapabilities
import org.njarasoa.fijerena.core.ui.di.AppContainer

/**
 * The sections the profile browses and its Browse entries
 * (docs/plans/20261012_multi-source-search-history-plan.md → Phase 6, O1).
 */
class ProfileSectionsTest {
    private fun source(
        id: Long,
        type: String = "XTREAM",
    ) = ProviderEntity(id = id, name = "S$id", url = "", username = "", type = type)

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `the sections are the union of the Xtream sources', other sources are browsed on their own`() {
        val sections =
            ProfileSections.of(
                listOf(source(1), source(2, "JELLYFIN"), source(3), source(4, "REMOTE_M3U")),
                emptySet(),
            )

        assertEquals(setOf(ContentType.LIVE_TV, ContentType.MOVIES, ContentType.TV_SHOWS), sections.merged)
        // Every non-Xtream source, in the profile's order.
        assertEquals(listOf(BrowseSource(2, "S2"), BrowseSource(4, "S4")), sections.browse)
        assertEquals(listOf(1L, 3L), sections.xtreamIds)
        assertFalse(sections.hasNoSources)
    }

    @Test
    fun `Live TV leaves the sections only when no Xtream source has channels`() {
        val sources = listOf(source(1), source(3))

        assertEquals(
            setOf(ContentType.LIVE_TV, ContentType.MOVIES, ContentType.TV_SHOWS),
            ProfileSections.of(sources, setOf(1)).merged,
        )
        assertEquals(setOf(ContentType.MOVIES, ContentType.TV_SHOWS), ProfileSections.of(sources, setOf(1, 3)).merged)
    }

    @Test
    fun `with no Xtream source there are no sections, only a Browse entry per source`() {
        val sections = ProfileSections.of(listOf(source(2, "JELLYFIN"), source(4, "REMOTE_M3U")), emptySet())

        assertTrue(sections.merged.isEmpty())
        assertEquals(listOf(BrowseSource(2, "S2"), BrowseSource(4, "S4")), sections.browse)
        assertFalse(sections.hasNoSources)
    }

    @Test
    fun `a profile with no source has nothing to browse`() {
        val sections = ProfileSections.of(emptyList(), emptySet())

        assertTrue(sections.merged.isEmpty())
        assertTrue(sections.browse.isEmpty())
        assertTrue(sections.hasNoSources)
    }

    @Test
    fun `a Jellyfin source's Browse entry opens its own sections, in section order`() {
        val context = mockk<Context>(relaxed = true)
        every { context.applicationContext } returns context
        val jellyfin = source(2, "JELLYFIN")
        mockkConstructor(ProviderRepository::class)
        coEvery { anyConstructed<ProviderRepository>().getProviderById(2) } returns jellyfin
        every { anyConstructed<ProviderRepository>().hasLogin(jellyfin) } returns true
        val container = mockk<AppContainer>()
        mockkObject(AppContainer.Companion)
        every { AppContainer.getInstance(any()) } returns container
        val provider = mockk<MediaProvider>()
        every { provider.capabilities } returns
            ProviderCapabilities(
                supportedContentTypes = setOf(ContentType.TV_SHOWS, ContentType.MOVIES),
                supportsEpg = false,
                supportsSearch = true,
                supportsAuthentication = true,
                supportsProgressSync = true,
            )
        val repo = mockk<MediaRepository>()
        every { repo.getProvider() } returns provider
        coEvery { container.getMediaRepository(2) } returns repo

        val opening = runBlocking { ProfileSections.opening(context, 2) }

        assertEquals(ProfileSections.Opening.Sections(listOf(ContentType.MOVIES, ContentType.TV_SHOWS)), opening)
    }

    @Test
    fun `a Jellyfin source with no login opens its sign-in`() {
        val context = mockk<Context>(relaxed = true)
        every { context.applicationContext } returns context
        val jellyfin = source(2, "JELLYFIN")
        mockkConstructor(ProviderRepository::class)
        coEvery { anyConstructed<ProviderRepository>().getProviderById(2) } returns jellyfin
        every { anyConstructed<ProviderRepository>().hasLogin(jellyfin) } returns false

        assertEquals(ProfileSections.Opening.SignIn, runBlocking { ProfileSections.opening(context, 2) })
    }
}
