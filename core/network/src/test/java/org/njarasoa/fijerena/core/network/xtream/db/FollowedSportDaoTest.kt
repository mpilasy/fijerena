package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.sync.SyncKind
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The followed teams and leagues' storage and their live sync bookkeeping, against a real
 * in-memory `xtream_v2.db` with the sync triggers installed: every follow is queued under the
 * no-provider id with `<kind>:<source>` as the content type, an unfollow leaves a tombstone,
 * following again clears it, and linking seeds the list. See the sports teams plan, Phase 3.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FollowedSportDaoTest {
    private lateinit var db: XtreamDatabase
    private lateinit var followed: FollowedSportDao

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
        followed = db.followedSportDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `following a team queues it for sync without a provider`() =
        runBlocking {
            followed.upsertClearingTombstone(arsenal())

            val version = db.syncVersionDao().get(SyncKind.FOLLOWED_SPORT_PROVIDER_ID, PROFILE, SyncKind.FOLLOWED_SPORT, "359", KEY_TYPE)
            assertNotNull(version)
            assertEquals(true, version!!.pending)
        }

    @Test
    fun `following again queues one version`() =
        runBlocking {
            followed.upsertClearingTombstone(arsenal())
            followed.upsertClearingTombstone(arsenal().copy(logoUrl = "https://a/new.png"))

            assertEquals(1, db.syncVersionDao().getPending(10).size)
            assertEquals("https://a/new.png", followed.get(PROFILE, "TEAM", "espn", "359")!!.logoUrl)
        }

    @Test
    fun `unfollowing records a tombstone and following again clears it`() =
        runBlocking {
            followed.upsertClearingTombstone(arsenal())

            followed.deleteRecordingTombstone(PROFILE, "TEAM", "espn", "359")
            assertNull(followed.get(PROFILE, "TEAM", "espn", "359"))
            val tombstone =
                db.syncTombstoneDao().get(SyncKind.FOLLOWED_SPORT_PROVIDER_ID, PROFILE, SyncKind.FOLLOWED_SPORT, "359", KEY_TYPE)
            assertNotNull(tombstone)
            assert(tombstone!!.deletedAt > 0) { "the trigger stamps the tombstone with the sync clock" }

            followed.upsertClearingTombstone(arsenal())
            assertNull(db.syncTombstoneDao().get(SyncKind.FOLLOWED_SPORT_PROVIDER_ID, PROFILE, SyncKind.FOLLOWED_SPORT, "359", KEY_TYPE))
        }

    @Test
    fun `a team and a league with the same id are two items`() =
        runBlocking {
            followed.upsertClearingTombstone(arsenal())
            followed.upsertClearingTombstone(arsenal().copy(kind = "LEAGUE", league = null, name = "Some League"))

            assertEquals(2, followed.getAll(PROFILE).size)
            assertTrue(followed.isFollowed(PROFILE, "TEAM", "espn", "359"))
            assertTrue(followed.isFollowed(PROFILE, "LEAGUE", "espn", "359"))
            assertFalse(followed.isFollowed(PROFILE, "TEAM", "nhl", "359"))
        }

    @Test
    fun `observe emits the profile's items in the order they were followed`() =
        runBlocking {
            followed.upsert(arsenal().copy(itemId = "2", name = "B", createdAt = 20))
            followed.upsert(arsenal().copy(itemId = "1", name = "A", createdAt = 10))
            followed.upsert(arsenal().copy(profileId = "other"))

            assertEquals(listOf("1", "2"), followed.observe(PROFILE).first().map { it.itemId })
        }

    @Test
    fun `linking seeds every item at the time it was followed`() =
        runBlocking {
            db.syncVersionDao().setApplying(true)
            followed.upsert(arsenal().copy(createdAt = 30))
            db.syncVersionDao().setApplying(false)

            db.syncVersionDao().seedFollowedSports()

            val version = db.syncVersionDao().get(SyncKind.FOLLOWED_SPORT_PROVIDER_ID, PROFILE, SyncKind.FOLLOWED_SPORT, "359", KEY_TYPE)
            assertEquals(30L, version!!.hlc)
        }

    @Test
    fun `deleting a profile drops its items only`() =
        runBlocking {
            followed.upsert(arsenal())
            followed.upsert(arsenal().copy(profileId = "other"))

            followed.deleteProfile(PROFILE)

            assertEquals(emptyList<FollowedSportEntity>(), followed.getAll(PROFILE))
            assertEquals(1, followed.getAll("other").size)
        }

    @Test
    fun `the sync content type reads back`() {
        assertEquals("TEAM" to "espn", FollowedSportEntity.parseSyncContentType(FollowedSportEntity.syncContentType("TEAM", "espn")))
        assertNull(FollowedSportEntity.parseSyncContentType("MOVIES"))
        assertNull(FollowedSportEntity.parseSyncContentType(":espn"))
        assertNull(FollowedSportEntity.parseSyncContentType("TEAM:"))
    }

    @Test
    fun `alternative names read back, and bad text reads as none`() {
        val names = listOf("Gunners", "ARS", "Arsenal \"FC\"")
        assertEquals(names, FollowedSportEntity.decodeAltNames(FollowedSportEntity.encodeAltNames(names)))
        assertEquals(emptyList<String>(), FollowedSportEntity.decodeAltNames("not json"))
    }

    private fun arsenal() =
        FollowedSportEntity(
            profileId = PROFILE,
            kind = "TEAM",
            source = "espn",
            itemId = "359",
            league = "soccer/eng.1",
            name = "Arsenal",
            shortName = "ARS",
            altNames = FollowedSportEntity.encodeAltNames(listOf("Gunners")),
            sport = "soccer",
            logoUrl = "https://a/359.png",
            createdAt = 1,
        )

    private companion object {
        const val PROFILE = "default"
        const val KEY_TYPE = "TEAM:espn"
    }
}
