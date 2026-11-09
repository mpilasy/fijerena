package org.njarasoa.fijerena.core.ui.sync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.network.provider.CategoryFilters
import org.njarasoa.fijerena.core.network.provider.ParentalRules
import org.njarasoa.fijerena.core.network.sync.SyncPayloads
import org.njarasoa.fijerena.core.network.sync.SyncPayloads.PlayAnswer
import org.njarasoa.fijerena.core.player.domain.ContentType

/** A request's checks on the TV, one for each answer, and the route it opens (the send to TV plan, Phase 2). */
class PlayRequestResolverTest {
    private class Facts(
        var provider: Long? = 7L,
        var profile: String? = "Anna",
        var hasSource: Boolean = true,
        var free: Boolean = true,
        var allowed: Boolean = true,
    ) : PlayRequestResolver.Facts {
        override suspend fun providerId(providerKey: String) = provider

        override suspend fun profileName(profileId: String) = profile

        override suspend fun profileHasSource(
            profileId: String,
            providerId: Long,
        ) = hasSource

        override suspend fun canSwitchTo(profileId: String) = free

        override suspend fun allowed(
            profileId: String,
            providerId: Long,
            request: SyncPayloads.PlayRequest,
        ) = allowed
    }

    private val request =
        SyncPayloads.PlayRequest(
            requestId = "r1",
            profileId = "anna",
            providerKey = "pk",
            streamId = "42",
            streamName = "Malcolm X",
            categoryId = "c1",
            contentType = ContentType.MOVIES,
            episodeExtension = "mkv",
            positionMs = 2_530_000L,
            sentAt = 0,
        )

    private fun resolve(
        facts: Facts = Facts(),
        request: SyncPayloads.PlayRequest = this.request,
    ) = runBlocking { PlayRequestResolver(facts).resolve(request) }

    private fun refused(result: String) = PlayResolution.Refused(result)

    @Test
    fun `an unknown source answers no_source`() {
        assertEquals(refused(PlayAnswer.NO_SOURCE), resolve(Facts(provider = null)))
    }

    @Test
    fun `a source off for the profile answers no_source`() {
        assertEquals(refused(PlayAnswer.NO_SOURCE), resolve(Facts(hasSource = false)))
    }

    @Test
    fun `an unknown profile answers no_profile`() {
        assertEquals(refused(PlayAnswer.NO_PROFILE), resolve(Facts(profile = null)))
    }

    @Test
    fun `a locked switch answers not_allowed`() {
        assertEquals(refused(PlayAnswer.NOT_ALLOWED), resolve(Facts(free = false)))
    }

    @Test
    fun `an item the profile may not open answers not_allowed`() {
        assertEquals(refused(PlayAnswer.NOT_ALLOWED), resolve(Facts(allowed = false)))
    }

    @Test
    fun `a playable item opens the player on this TV's source at the phone's position`() {
        val playable = resolve() as PlayResolution.Playable

        assertEquals("anna", playable.profileId)
        assertEquals("Anna", playable.profileName)
        assertEquals(7L, playable.route.providerId)
        assertEquals("42", playable.route.streamId)
        assertEquals("mkv", playable.route.episodeExtension)
        assertEquals(2_530_000L, playable.route.startPositionMs)
    }

    @Test
    fun `a channel has no position`() {
        val live = request.copy(contentType = ContentType.LIVE_TV, positionMs = 9_000L)

        val playable = resolve(request = live) as PlayResolution.Playable

        assertEquals(null, playable.route.startPositionMs)
    }

    private val child = ParentalRules(hiddenTitles = listOf("south park"), exceptions = listOf("pokemon"))
    private val childFilters = CategoryFilters(child = child)

    @Test
    fun `a grown-up plays an item of an unknown category`() {
        assertTrue(PlayRequestResolver.itemAllowed(CategoryFilters(), null, films = true, listOf("Malcolm X"), knownCatalogue = true))
    }

    @Test
    fun `a child does not play an item of an unknown category on an Xtream source`() {
        assertFalse(PlayRequestResolver.itemAllowed(childFilters, null, films = true, listOf("Malcolm X"), knownCatalogue = true))
    }

    @Test
    fun `a child plays a kids category and a hidden title stays out`() {
        assertTrue(PlayRequestResolver.itemAllowed(childFilters, "kids", films = true, listOf("Bluey"), knownCatalogue = true))
        assertFalse(PlayRequestResolver.itemAllowed(childFilters, "kids", films = true, listOf("South Park"), knownCatalogue = true))
    }

    @Test
    fun `a child plays an exception from a category the filters hide`() {
        assertTrue(PlayRequestResolver.itemAllowed(childFilters, "drama", films = true, listOf("Pokemon"), knownCatalogue = true))
        assertFalse(PlayRequestResolver.itemAllowed(childFilters, "drama", films = true, listOf("Bluey"), knownCatalogue = true))
    }

    @Test
    fun `a child on a source without categories keeps the title rule only`() {
        assertTrue(PlayRequestResolver.itemAllowed(childFilters, null, films = true, listOf("Bluey"), knownCatalogue = false))
        assertFalse(PlayRequestResolver.itemAllowed(childFilters, null, films = true, listOf("South Park"), knownCatalogue = false))
    }
}
