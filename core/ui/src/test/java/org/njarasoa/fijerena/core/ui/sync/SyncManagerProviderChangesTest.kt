package org.njarasoa.fijerena.core.ui.sync

import android.app.Application
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.sync.SyncAccountStore
import org.njarasoa.fijerena.core.network.sync.SyncApi
import org.njarasoa.fijerena.core.network.sync.SyncEngine
import org.njarasoa.fijerena.core.ui.di.AppContainer

/**
 * Providers a sync pass changed reach `AppContainer`, which drops their cached repository. See
 * docs/plans/20261002_next-level-rock-solid-resilience-plan.md → R-06.
 */
class SyncManagerProviderChangesTest {
    private val dispatcher = StandardTestDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val store = mockk<SyncAccountStore>(relaxed = true)
    private val engine = mockk<SyncEngine>()
    private val container = mockk<AppContainer>(relaxed = true)
    private val listener = slot<SyncEngine.Listener>()
    private lateinit var manager: SyncManager

    @Before
    fun setup() {
        every { engine.isLinked } returns true
        coEvery { engine.syncNow(capture(listener)) } returns SyncEngine.Outcome(0, 0, 0, false)
        mockkObject(AppContainer.Companion)
        every { AppContainer.getInstance(any()) } returns container
        manager = SyncManager(mockk<Application>(relaxed = true), scope, store, engine, mockk<SyncApi>(relaxed = true))
        manager.requestSync(0)
        dispatcher.scheduler.runCurrent()
    }

    @After
    fun tearDown() {
        scope.cancel()
        unmockkAll()
    }

    @Test
    fun `providers changed by a pass are evicted from the container`() {
        listener.captured.onProvidersChanged(setOf(3L, 4L))
        dispatcher.scheduler.runCurrent()

        coVerify { container.onProvidersChanged(setOf(3L, 4L)) }
    }
}
