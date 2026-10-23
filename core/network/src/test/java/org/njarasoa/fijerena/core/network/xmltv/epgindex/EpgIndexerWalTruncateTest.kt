package org.njarasoa.fijerena.core.network.xmltv.epgindex

import android.content.Context
import android.database.MatrixCursor
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test

/**
 * After an import the guide database's WAL is truncated, also when there are no free pages to
 * vacuum: on a Shield it stayed at 231 MB.
 */
class EpgIndexerWalTruncateTest {
    private val sdb = mockk<SupportSQLiteDatabase>(relaxed = true)

    @After
    fun tearDown() = unmockkAll()

    @Test
    fun `the vacuum after an import truncates the WAL with no free pages`() =
        runBlocking {
            every { sdb.query(any<String>()) } answers { MatrixCursor(arrayOf("n")).apply { addRow(arrayOf(0L)) } }
            val helper = mockk<SupportSQLiteOpenHelper> { every { writableDatabase } returns sdb }
            val db = mockk<EpgIndexDatabase> { every { openHelper } returns helper }
            mockkObject(EpgIndexDatabase.Companion)
            every { EpgIndexDatabase.getInstance(any()) } returns db
            val context = mockk<Context> { every { applicationContext } returns this }

            EpgIndexer.getInstance(context).incrementalVacuum()

            verify { sdb.query("PRAGMA wal_checkpoint(TRUNCATE)") }
        }
}
