package org.njarasoa.fijerena.core.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which `media_cache_<id>` keys deleting the Default profile removes — see `MediaRepository.clearDefaultProfile`. */
class DefaultProfileKeysTest {
    @Test
    fun `recent categories and bookmarks are the Default profile's`() {
        listOf("recent_categories_MOVIES", "recent_categories_LIVE_TV", "last_content_type", "last_movies_item")
            .forEach { assertTrue(it, MediaRepository.isDefaultProfileKey(it)) }
    }

    @Test
    fun `migration flags are the provider's, not the profile's`() {
        listOf("watch_state_migrated_v1", "favorites_migrated_v1")
            .forEach { assertFalse(it, MediaRepository.isDefaultProfileKey(it)) }
    }
}
