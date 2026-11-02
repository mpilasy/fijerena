package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A play that writes an empty name (a player opened without the stream's name) keeps the name a
 * history entry already has, in both watch-state upserts: an empty name showed as "No title" in
 * Recent. Against a real in-memory `xtream_v2.db` under Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WatchStateEmptyNameTest {
    private lateinit var db: XtreamDatabase

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, XtreamDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun recency(name: String) {
        db.watchStateDao().upsertRecency(P, PROFILE, ID, ContentType.MOVIES, name, "cat", 1L, null, null, null, null, null, null)
    }

    private fun progress(name: String) {
        db.watchStateDao().upsertProgress(
            P,
            PROFILE,
            ID,
            ContentType.MOVIES,
            name,
            "cat",
            10L,
            100L,
            false,
            2L,
            null,
            null,
            null,
            null,
            null,
            null,
        )
    }

    private fun storedName(): String? = runBlocking { db.watchStateDao().getItem(P, PROFILE, ID, ContentType.MOVIES)?.itemName }

    @Test
    fun anEmptyNameOnAPlayKeepsTheStoredName() {
        recency("The Godfather")
        recency("")

        assertEquals("The Godfather", storedName())
    }

    @Test
    fun anEmptyNameOnAProgressWriteKeepsTheStoredName() {
        recency("The Godfather")
        progress("")

        assertEquals("The Godfather", storedName())
    }

    @Test
    fun aNewNameStillReplacesTheOldOne() {
        recency("Godfather")
        progress("The Godfather")

        assertEquals("The Godfather", storedName())
    }

    private companion object {
        const val P = 1L
        const val PROFILE = "default"
        const val ID = "42"
    }
}
