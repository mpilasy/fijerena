package org.njarasoa.fijerena.core.network.xmltv.epgindex

import androidx.room.RoomDatabase
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Guides and a database that is full, for the full disk tests (the low storage plan, Phase 3). */
internal object FullDiskGuide {
    private val stamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC)

    /** An XMLTV guide of channel [channelId] with [count] programmes of 10 minutes from tomorrow on. */
    fun xml(
        channelId: String,
        title: String,
        count: Int,
    ): String =
        buildString {
            val start = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS)
            append("""<?xml version="1.0" encoding="UTF-8"?><tv>""")
            append("""<channel id="$channelId"><display-name>$channelId</display-name></channel>""")
            repeat(count) { i ->
                val from = stamp.format(start.plusSeconds(600L * i))
                val to = stamp.format(start.plusSeconds(600L * (i + 1)))
                append("""<programme start="$from +0000" stop="$to +0000" channel="$channelId">""")
                append("""<title>$title $i</title><desc>${"A description that takes some room. ".repeat(4)}</desc></programme>""")
            }
            append("</tv>")
        }

    /**
     * Lets [db] grow by [extraPages] pages at most (`PRAGMA max_page_count`): a write past that
     * fails with SQLITE_FULL, as on a full disk.
     */
    fun capPages(
        db: RoomDatabase,
        extraPages: Int,
    ) {
        val sdb = db.openHelper.writableDatabase
        val pages = count(db, "PRAGMA page_count")
        sdb.query("PRAGMA max_page_count = ${pages + extraPages}").use { it.moveToFirst() }
    }

    fun count(
        db: RoomDatabase,
        sql: String,
    ): Int =
        db.openHelper.writableDatabase
            .query(sql)
            .use { if (it.moveToFirst()) it.getInt(0) else 0 }
}
