package org.njarasoa.fijerena.core.network.sports

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.njarasoa.fijerena.core.network.sports.GameChannelFixtures.epoch
import org.njarasoa.fijerena.core.network.xmltv.GuideScope
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgChannelEntity
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgIndexDatabase
import org.njarasoa.fijerena.core.network.xmltv.epgindex.EpgProgrammeEntity
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** [EpgGuideLookup] on an in-memory guide index: the window is bounded both ways, the FTS query and the LIKE fallback. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GameChannelGuideLookupTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: EpgIndexDatabase

    // Guide source 11 belongs to source 1; guide source 99 is not in the profile.
    private val scope = GuideScope(emptyList(), mapOf(11L to 1L))

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, EpgIndexDatabase::class.java).allowMainThreadQueries().build()
        runBlocking {
            val dao = db.epgIndexDao()
            dao.insertChannels(
                listOf(
                    EpgChannelEntity("DAZN1.fr", "DAZN 1", sourceId = 11),
                    EpgChannelEntity("CANAL+FOOT.fr", "CANAL+FOOT", sourceId = 11),
                    EpgChannelEntity("Other.fr", "Other", sourceId = 99),
                ),
            )
            dao.insertProgrammes(
                listOf(
                    row("DAZN1.fr", "Live: Ligue 1 – PSG / OM", "2026-11-08T19:30:00Z", "2026-11-08T22:00:00Z"),
                    row("CANAL+FOOT.fr", "Paris FC / Paris-SG", "2026-11-08T19:00:00Z", "2026-11-08T21:00:00Z"),
                    // Running at the window's start but started long before: not returned.
                    row("CANAL+FOOT.fr", "Le Mag PSG", "2026-11-08T14:00:00Z", "2026-11-08T19:00:00Z"),
                    // After the window.
                    row("DAZN1.fr", "PSG / OM : le débrief", "2026-11-08T22:00:00Z", "2026-11-08T23:00:00Z"),
                    // Not a guide source of the profile.
                    row("Other.fr", "PSG / OM", "2026-11-08T19:30:00Z", "2026-11-08T22:00:00Z", sourceId = 99),
                ),
            )
            db.openHelper.writableDatabase.execSQL("INSERT INTO epg_programme_fts(epg_programme_fts) VALUES('rebuild')")
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun row(
        channelId: String,
        title: String,
        start: String,
        end: String,
        sourceId: Long = 11,
    ) = EpgProgrammeEntity(
        channelId = channelId,
        title = title,
        titleLowercase = title.lowercase(),
        startEpoch = epoch(start),
        endEpoch = epoch(end),
        sourceId = sourceId,
    )

    private val from = epoch("2026-11-08T18:45:00Z")
    private val to = epoch("2026-11-08T21:45:00Z")

    @Test
    fun `the FTS search finds any of the terms in the window, on the profile's guide sources`() =
        runBlocking {
            val lookup = EpgGuideLookup(db.epgIndexDao(), scope)
            val found = lookup.searchTitles(listOf("Paris Saint-Germain", "Paris SG", "PSG", "OM"), from, to)
            assertEquals(listOf("Paris FC / Paris-SG", "Live: Ligue 1 – PSG / OM"), found.map { it.title })
            assertEquals(listOf(1L, 1L), found.map { it.providerId })
            assertEquals("DAZN 1", found[1].channelName)
        }

    @Test
    fun `the LIKE fallback searches titles for each term while the FTS index is stale`() =
        runBlocking {
            val lookup = EpgGuideLookup(db.epgIndexDao(), scope, isFtsStale = { true })
            val found = lookup.searchTitles(listOf("Paris SG", "PSG"), from, to)
            // A substring of the raw title: the folded "paris sg" does not find "Paris-SG".
            assertEquals(listOf("Live: Ligue 1 – PSG / OM"), found.map { it.title })
        }

    @Test
    fun `programmes on channels keep the ones that start in the window`() =
        runBlocking {
            val lookup = EpgGuideLookup(db.epgIndexDao(), scope)
            val found = lookup.programmesOnChannels(listOf("DAZN1.fr", "CANAL+FOOT.fr"), from, to)
            assertEquals(listOf("Paris FC / Paris-SG", "Live: Ligue 1 – PSG / OM"), found.map { it.title })
        }

    @Test
    fun `no index means no programmes`() =
        runBlocking {
            val lookup = EpgGuideLookup(db.epgIndexDao(), scope, isIndexed = { false })
            assertEquals(emptyList<GuideProgramme>(), lookup.searchTitles(listOf("PSG"), from, to))
        }

    @Test
    fun `the FTS query quotes each folded term and joins them with OR`() {
        assertEquals(
            "\"paris saint germain\" OR \"psg\" OR \"barca\"",
            EpgGuideLookup.ftsQuery(listOf("Paris Saint-Germain", "PSG", "psg", "Barça")),
        )
    }
}
