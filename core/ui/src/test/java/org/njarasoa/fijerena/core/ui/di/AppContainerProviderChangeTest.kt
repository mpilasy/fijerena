package org.njarasoa.fijerena.core.ui.di

import android.content.Context
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.MediaProviderFactory
import org.njarasoa.fijerena.core.network.MediaRepository

/**
 * A provider's settings, URL or login changed: the container drops its cached MediaRepository and
 * the factory's provider together. Dropping only the factory's copy left the repository
 * reconnecting its old, disconnected provider with the old login. See
 * docs/plans/archive/20261002_next-level-rock-solid-resilience-plan.md → R-06 step 2.
 */
class AppContainerProviderChangeTest {
    private val context = mockk<Context>(relaxed = true)
    private val changed = mockk<MediaRepository>(relaxed = true)
    private val untouched = mockk<MediaRepository>(relaxed = true)
    private lateinit var container: AppContainer

    @Before
    fun setup() {
        every { context.applicationContext } returns context
        mockkObject(MediaProviderFactory)
        every { MediaProviderFactory.clearCache(any()) } just runs
        container = AppContainer(context)
        container.mediaRepositories[CHANGED] = changed
        container.mediaRepositories[UNTOUCHED] = untouched
    }

    @After
    fun tearDown() {
        MediaProviderFactory.providerChangedListener = null
        unmockkAll()
    }

    @Test
    fun `a changed provider loses its cached repository and the factory's provider`() =
        runBlocking {
            container.onProvidersChanged(setOf(CHANGED))

            assertEquals(setOf(UNTOUCHED), container.mediaRepositories.keys)
            verify { changed.close() }
            verify(exactly = 0) { untouched.close() }
            verify { MediaProviderFactory.clearCache(CHANGED) }
        }

    @Test
    fun `a change announced through the factory evicts the repository too`() {
        MediaProviderFactory.providerChanged(CHANGED)

        // The eviction runs on the container's own scope; wait for it.
        val deadline = System.currentTimeMillis() + 5_000
        while (CHANGED in container.mediaRepositories && System.currentTimeMillis() < deadline) Thread.sleep(10)

        assertEquals(setOf(UNTOUCHED), container.mediaRepositories.keys)
    }

    private companion object {
        const val CHANGED = 7L
        const val UNTOUCHED = 8L
    }
}
