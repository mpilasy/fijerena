package org.njarasoa.fijerena.feature.sendtotv

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.njarasoa.fijerena.core.navigation.Screen
import org.njarasoa.fijerena.core.network.sync.PlayRequests
import org.njarasoa.fijerena.core.network.sync.SyncPayloads
import org.njarasoa.fijerena.core.network.sync.SyncPayloads.PlayAnswer
import org.njarasoa.fijerena.core.ui.sync.PlayRequestResolver
import org.njarasoa.fijerena.core.ui.sync.PlayResolution

/** A request's route through the TV: answers, states and replacement (the send to TV plan, Phase 3). */
class SendRequestHandlerTest {
    private val answers = mutableListOf<Pair<String, String>>()
    private val played = mutableListOf<PlayResolution.Playable>()
    private var refuseWith: String? = null
    private val now = 1_800_000_000_000L

    private val resolver =
        PlayRequestResolver(
            object : PlayRequestResolver.Facts {
                override suspend fun providerId(providerKey: String): Long = 7L

                override suspend fun profileName(profileId: String): String? = if (refuseWith == PlayAnswer.NO_PROFILE) null else "Anna"

                override suspend fun profileHasSource(
                    profileId: String,
                    providerId: Long,
                ): Boolean = refuseWith != PlayAnswer.NO_SOURCE

                override suspend fun canSwitchTo(profileId: String): Boolean = true

                override suspend fun allowed(
                    profileId: String,
                    providerId: Long,
                    request: SyncPayloads.PlayRequest,
                ): Boolean = refuseWith != PlayAnswer.NOT_ALLOWED
            },
        )

    private val handler =
        SendRequestHandler(
            resolver,
            answer = { id, result -> answers += id to result },
            done = {},
            now = { now },
        )

    private fun incoming(id: String) =
        PlayRequests.Incoming(
            SyncPayloads.PlayRequest(requestId = id, profileId = "anna", providerKey = "pk", streamId = "1", sentAt = now),
            hlc = 5,
            receivedAt = now,
        )

    private fun update(
        held: PlayRequests.Incoming?,
        isBusy: Boolean = false,
        activeProfile: String = "anna",
    ) = runBlocking { handler.update(held, isBusy, activeProfile) }

    @Test
    fun `a request answers received and shows the banner`() {
        update(incoming("r1"))

        assertEquals(listOf("r1" to PlayAnswer.RECEIVED), answers)
        assertEquals("r1", handler.banner?.id)
        assertNull(handler.banner?.forProfile)
    }

    @Test
    fun `on a busy screen it answers busy, no banner`() {
        update(incoming("r1"), isBusy = true)

        assertEquals(listOf("r1" to PlayAnswer.RECEIVED, "r1" to PlayAnswer.BUSY), answers)
        assertNull(handler.banner)
    }

    @Test
    fun `a refused request answers the refusal, no banner`() {
        listOf(PlayAnswer.NO_SOURCE, PlayAnswer.NO_PROFILE, PlayAnswer.NOT_ALLOWED).forEachIndexed { i, result ->
            refuseWith = result
            update(incoming("r$i"))
            assertEquals("r$i" to result, answers.last())
            assertNull(handler.banner)
        }
    }

    @Test
    fun `an expired request answers dismissed`() {
        update(incoming("r1").copy(receivedAt = now - PlayRequests.MAX_AGE_MS - 1))

        assertEquals("r1" to PlayAnswer.DISMISSED, answers.last())
        assertNull(handler.banner)
    }

    @Test
    fun `a request for another profile names it on the banner`() {
        update(incoming("r1"), activeProfile = "other")

        assertEquals("Anna", handler.banner?.forProfile)
    }

    @Test
    fun `a newer request replaces the banner and the old one answers dismissed`() {
        update(incoming("r1"))
        update(incoming("r2"))

        assertEquals("r2", handler.banner?.id)
        assertEquals(
            listOf("r1" to PlayAnswer.RECEIVED, "r1" to PlayAnswer.DISMISSED, "r2" to PlayAnswer.RECEIVED),
            answers,
        )
    }

    @Test
    fun `a closed request removes the banner without an answer`() {
        update(incoming("r1"))
        update(null)

        assertNull(handler.banner)
        assertEquals(listOf("r1" to PlayAnswer.RECEIVED), answers)
    }

    @Test
    fun `Dismiss answers dismissed once`() {
        update(incoming("r1"))
        val banner = handler.banner!!

        handler.dismiss(banner)
        handler.dismiss(banner)

        assertEquals(listOf("r1" to PlayAnswer.RECEIVED, "r1" to PlayAnswer.DISMISSED), answers)
        assertNull(handler.banner)
    }

    @Test
    fun `Play opens the item and answers playing, the banner plays nothing by itself`() {
        update(incoming("r1"))
        val banner = handler.banner!!

        runBlocking { handler.play(banner) { played += it } }

        assertEquals(1, played.size)
        assertEquals(Screen.Player::class, played.single().route::class)
        assertEquals(listOf("r1" to PlayAnswer.RECEIVED, "r1" to PlayAnswer.PLAYING), answers)
    }
}
