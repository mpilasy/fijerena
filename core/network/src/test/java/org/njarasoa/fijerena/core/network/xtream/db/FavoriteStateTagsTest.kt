package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.sync.SyncKind
import org.njarasoa.fijerena.core.network.sync.SyncPayloads
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A favourite's tags in a real in-memory `xtream_v2.db` with the sync triggers installed: setting
 * them queues the favourite for sync, re-favouriting keeps them, and a rename or a delete rewrites
 * the profile's tag on every provider and no other profile's. See the favourite tags plan, FT1.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FavoriteStateTagsTest {
    private lateinit var db: XtreamDatabase
    private lateinit var favorites: FavoriteStateDao

    @Before
    fun setUp() {
        db =
            Room
                .inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, XtreamDatabase::class.java)
                .allowMainThreadQueries()
                .addCallback(
                    object : RoomDatabase.Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) = XtreamSyncTriggers.install(db)
                    },
                ).build()
        favorites = db.favoriteStateDao()
    }

    @After
    fun tearDown() = db.close()

    private fun row(
        providerId: Long,
        itemId: String,
        tags: List<String> = emptyList(),
        profileId: String = PROFILE,
    ) = FavoriteStateEntity(
        providerId = providerId,
        profileId = profileId,
        itemId = itemId,
        contentType = "LIVE_TV",
        kind = FavoriteKind.STREAM,
        name = "Channel $itemId",
        createdAt = 1,
        tags = FavoriteStateEntity.encodeTags(tags),
    )

    private fun tags(
        providerId: Long,
        itemId: String,
        profileId: String = PROFILE,
    ) = FavoriteStateEntity.decodeTags(favorites.get(providerId, profileId, itemId, "LIVE_TV", FavoriteKind.STREAM)!!.tags)

    @Test
    fun `a new favourite has no tags`() {
        favorites.upsertClearingTombstone(row(1, "a"))

        assertEquals(FavoriteStateEntity.NO_TAGS, favorites.get(1, PROFILE, "a", "LIVE_TV", FavoriteKind.STREAM)!!.tags)
    }

    @Test
    fun `setting tags queues the favourite for sync with them`() =
        runBlocking {
            favorites.upsertClearingTombstone(row(1, "a"))
            db.syncVersionDao().getPending(10).forEach {
                db.syncVersionDao().markSent(it.providerId, it.profileId, it.kind, it.itemId, it.contentType, it.hlc)
            }

            favorites.setTags(1, PROFILE, "a", "LIVE_TV", FavoriteStateEntity.encodeTags(listOf("News", "Sat, Sun")))

            val pending = db.syncVersionDao().getPending(10).single()
            assertEquals(SyncKind.FAVORITE_STREAM, pending.kind)
            val payload = SyncPayloads.Favorite.of(favorites.get(1, PROFILE, "a", "LIVE_TV", FavoriteKind.STREAM)!!)
            assertEquals(listOf("News", "Sat, Sun"), payload.tags)
        }

    @Test
    fun `re-favouriting keeps the tags`() {
        favorites.upsertClearingTombstone(row(1, "a", listOf("News")))

        favorites.upsertClearingTombstone(row(1, "a").copy(createdAt = 2))

        assertEquals(listOf("News"), tags(1, "a"))
    }

    @Test
    fun `rename rewrites the profile's tag on every provider`() {
        favorites.upsertClearingTombstone(row(1, "a", listOf("news", "Kids")))
        favorites.upsertClearingTombstone(row(2, "b", listOf("News")))
        favorites.upsertClearingTombstone(row(2, "c", listOf("Headlines", "News")))
        favorites.upsertClearingTombstone(row(1, "a", listOf("News"), profileId = OTHER))

        favorites.renameTag(PROFILE, "News", "Headlines")

        assertEquals(listOf("Headlines", "Kids"), tags(1, "a"))
        assertEquals(listOf("Headlines"), tags(2, "b"))
        assertEquals(listOf("Headlines"), tags(2, "c"))
        assertEquals(listOf("News"), tags(1, "a", profileId = OTHER))
    }

    @Test
    fun `delete removes the profile's tag on every provider`() {
        favorites.upsertClearingTombstone(row(1, "a", listOf("News", "Kids")))
        favorites.upsertClearingTombstone(row(2, "b", listOf("news")))

        favorites.deleteTag(PROFILE, "NEWS")

        assertEquals(listOf("Kids"), tags(1, "a"))
        assertEquals(emptyList<String>(), tags(2, "b"))
        assertEquals(listOf(1L), favorites.getTagged(PROFILE).map { it.providerId })
    }

    @Test
    fun `a payload from an older build has no tags`() {
        val old = SyncPayloads.decode<SyncPayloads.Favorite>("""{"name":"BBC","createdAt":1}""")

        assertEquals(emptyList<String>(), old.tags)
    }

    private companion object {
        const val PROFILE = "p1"
        const val OTHER = "p2"
    }
}
