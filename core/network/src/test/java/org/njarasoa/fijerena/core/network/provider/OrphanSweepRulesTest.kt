package org.njarasoa.fijerena.core.network.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * What the automatic orphan sweep may delete. It decides "orphaned" by comparing against
 * `providers.db`, so whenever that file and `xtream_v2.db` disagree the inference is wrong; user
 * data must never be within its reach. See
 * docs/plans/20261002_next-level-rock-solid-resilience-plan.md → R-02.
 */
class OrphanSweepRulesTest {
    @Test
    fun `sweep never touches favourites, watch history or sync state`() {
        val userTables = listOf("favorite_state", "watch_state", "sync_version", "sync_tombstone", "sync_clock")
        userTables.forEach { table ->
            assertFalse("$table must not be swept", table in ProviderRepository.ORPHAN_SWEEP_TABLES)
        }
    }

    @Test
    fun `automatic sweep keeps every credential file, even of an unknown provider`() {
        val files = listOf("provider_creds_7.xml", "provider_creds_9.xml", "media_cache_9.xml", "xtream_cache_9.xml")

        val orphaned = ProviderRepository.orphanedPrefsFiles(files, validProviderIds = setOf(7L), includeCredentials = false)

        assertEquals(listOf("media_cache_9.xml", "xtream_cache_9.xml"), orphaned)
    }

    @Test
    fun `user-requested shrink also removes an unknown provider's credentials`() {
        val files = listOf("provider_creds_7.xml", "provider_creds_9.xml")

        val orphaned = ProviderRepository.orphanedPrefsFiles(files, validProviderIds = setOf(7L), includeCredentials = true)

        assertEquals(listOf("provider_creds_9.xml"), orphaned)
    }

    @Test
    fun `a profile's cache file belongs to its provider`() {
        val files = listOf("media_cache_7_profile_kid.xml", "media_cache_9_profile_kid.xml", "app_settings.xml", "media_cache_x.xml")

        val orphaned = ProviderRepository.orphanedPrefsFiles(files, validProviderIds = setOf(7L), includeCredentials = false)

        assertEquals(listOf("media_cache_9_profile_kid.xml"), orphaned)
    }
}
