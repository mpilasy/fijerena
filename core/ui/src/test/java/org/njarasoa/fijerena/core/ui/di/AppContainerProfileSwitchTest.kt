package org.njarasoa.fijerena.core.ui.di

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.MediaProviderFactory
import org.njarasoa.fijerena.core.network.MediaRepository
import org.njarasoa.fijerena.core.network.provider.ProviderRepository

/**
 * A profile switch moves no source (multi-source plan, Phase 6): it recomputes the hidden
 * categories for the new profile and drops every cached repository, so the lists reload over the
 * new profile's own sources — nothing reads the sources to pick one to move to.
 */
class AppContainerProfileSwitchTest {
    private val context = mockk<Context>(relaxed = true)
    private val repository = mockk<MediaRepository>(relaxed = true)
    private lateinit var container: AppContainer

    @Before
    fun setup() {
        every { context.applicationContext } returns context
        mockkObject(MediaProviderFactory)
        every { MediaProviderFactory.clearProfileScopedProviders() } just runs
        mockkConstructor(ProviderRepository::class)
        coEvery { anyConstructed<ProviderRepository>().applyCategoryFiltersForSwitch(any(), any()) } just runs
        container = AppContainer(context)
        container.mediaRepositories[SOURCE] = repository
    }

    @After
    fun tearDown() {
        MediaProviderFactory.providerChangedListener = null
        unmockkAll()
    }

    @Test
    fun `a profile switch drops the cached repositories and moves no source`() =
        runBlocking {
            container.switchProfile("kids")

            assertTrue(container.mediaRepositories.isEmpty())
            verify { repository.close() }
            coVerify { anyConstructed<ProviderRepository>().applyCategoryFiltersForSwitch(any(), "kids") }
            coVerify(exactly = 0) { anyConstructed<ProviderRepository>().getAllProvidersList() }
            coVerify(exactly = 0) { anyConstructed<ProviderRepository>().profileSourcesList(any()) }
        }

    private companion object {
        const val SOURCE = 7L
    }
}
