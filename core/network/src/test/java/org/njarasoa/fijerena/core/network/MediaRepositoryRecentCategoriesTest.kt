package org.njarasoa.fijerena.core.network

import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.fixtures.FakeFavoriteStateDao
import org.njarasoa.fijerena.core.network.fixtures.FakeWatchStateDao
import org.njarasoa.fijerena.core.network.profile.ProfileEntity
import org.njarasoa.fijerena.core.player.domain.ContentType

/**
 * Recent Categories come from the memory copy: the prefs commit runs later on another thread, so
 * a second open read from the prefs lost the first one. See the thread races plan, 8.
 */
class MediaRepositoryRecentCategoriesTest {
    private lateinit var repository: MediaRepository
    private lateinit var prefs: SharedPreferences

    @Before
    fun setup() {
        mockkStatic(Looper::class)
        every { Looper.getMainLooper() } returns mockk(relaxed = true)
        val context = mockk<Context>(relaxed = true)
        // The prefs never show a commit: as if each commit had not run yet.
        prefs = mockk(relaxed = true)
        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { prefs.getString(any(), any()) } returns null
        repository =
            MediaRepository(
                context,
                1L,
                ProfileEntity.DEFAULT_ID,
                watchStateDao = FakeWatchStateDao(),
                favoriteStateDao = FakeFavoriteStateDao(),
            )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `two quick opens before the prefs commit keep both categories`() {
        repository.addToCategoryHistory("cat1", "News", ContentType.LIVE_TV)
        repository.addToCategoryHistory("cat2", "Sport", ContentType.LIVE_TV)

        assertEquals(listOf("cat2", "cat1"), repository.getRecentlyViewedCategories(ContentType.LIVE_TV).map { it.categoryId })
    }

    @Test
    fun `opens from many threads at once lose no category`() {
        val threads =
            (1..5).map { i ->
                Thread { repository.addToCategoryHistory("cat$i", "Category $i", ContentType.MOVIES) }
            }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        assertEquals(
            (1..5).map { "cat$it" }.toSet(),
            repository.getRecentlyViewedCategories(ContentType.MOVIES).map { it.categoryId }.toSet(),
        )
    }

    @Test
    fun `a cleared list does not come back from prefs that the clear has not reached yet`() {
        // The prefs still hold a list: the clear's commit has not run yet.
        every { prefs.getString(any(), any()) } returns
            "[{\"categoryId\":\"old\",\"categoryName\":\"Old\",\"contentType\":\"live\",\"timestamp\":1}]"
        repository.clearCache()

        assertEquals(emptyList<String>(), repository.getRecentlyViewedCategories(ContentType.LIVE_TV).map { it.categoryId })
    }
}
