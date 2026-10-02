package org.njarasoa.fijerena.core.network.xtream.db

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The JVM half of F-20/F-33: `xtream_v2.db` holds watch history and favourites, and Room's
 * destructive fallback now covers only pre-v7 files — so a version bump without its `Migration`
 * no longer wipes the data silently, it fails to open. These catch that before a device does:
 * an unbroken chain of migrations from v7 to [XtreamDatabase.DB_VERSION], and a committed schema
 * for every version since the history starts. The upgrade itself, with data, runs on a device:
 * `XtreamDatabaseUpgradeTest` (androidTest). See
 * docs/plans/20261001_rock-solid-stability-resilience-plan.md → F-20, F-33.
 */
class XtreamDatabaseMigrationChainTest {
    private val schemaDir = File("schemas/${XtreamDatabase::class.java.name}")

    @Test
    fun `every version from 7 up has exactly one migration to the next`() {
        val steps = XtreamDatabase.ALL_MIGRATIONS.map { it.startVersion to it.endVersion }
        val expected = (7 until XtreamDatabase.DB_VERSION).map { it to it + 1 }
        assertEquals(expected, steps)
    }

    @Test
    fun `every version since the schema history starts has its schema committed`() {
        val versions = schemaDir.listFiles { f -> f.extension == "json" }.orEmpty().mapNotNull { it.nameWithoutExtension.toIntOrNull() }.sorted()
        assertTrue("no exported schemas under ${schemaDir.absolutePath}", versions.isNotEmpty())
        assertEquals((versions.first()..XtreamDatabase.DB_VERSION).toList(), versions)

        val latest = Json.parseToJsonElement(File(schemaDir, "${XtreamDatabase.DB_VERSION}.json").readText())
        assertEquals(XtreamDatabase.DB_VERSION, latest.jsonObject.getValue("database").jsonObject.getValue("version").jsonPrimitive.int)
    }
}
