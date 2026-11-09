package org.njarasoa.fijerena.core.ui.viewmodels

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.sync.SyncPayloads
import org.njarasoa.fijerena.core.network.sync.SyncPayloads.PlayAnswer
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.ui.R

/** The phone's life of a request: picker, immediate send, timeouts, close and messages (the send to TV plan, Phase 4). */
@OptIn(ExperimentalCoroutinesApi::class)
class SendToTvViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private class Fake : SendToTvGateway {
        override val available: Flow<Boolean> = MutableStateFlow(true)
        val outgoing = MutableSharedFlow<PlayAnswer>(extraBufferCapacity = 8)
        override val answers = outgoing
        var rowList = listOf(TvRow("tv", "Living Room", TvStatus.OPEN))
        val sent = mutableListOf<Pair<String, SyncPayloads.PlayRequest>>()
        val closed = mutableListOf<SyncPayloads.PlayRequest>()
        val stopped = mutableListOf<String>()
        val notices = mutableListOf<Pair<Int, List<Any>>>()
        var buildResult: SyncPayloads.PlayRequest? = request("r1")
        var positionPassed: Long? = -1

        override suspend fun rows() = rowList

        override suspend fun buildRequest(
            item: SendItem,
            position: Long?,
        ): SyncPayloads.PlayRequest? {
            positionPassed = position
            return buildResult
        }

        override fun send(
            targetDeviceId: String,
            request: SyncPayloads.PlayRequest,
        ) {
            sent += targetDeviceId to request
        }

        override fun close(
            targetDeviceId: String,
            request: SyncPayloads.PlayRequest,
        ) {
            closed += request
        }

        override fun stopWaiting(requestId: String) {
            stopped += requestId
        }

        override fun notify(
            text: Int,
            vararg args: Any,
        ) {
            notices += text to args.toList()
        }
    }

    private val fake = Fake()
    private val item = SendItem(1L, "42", "Malcolm X", "c1", ContentType.MOVIES)

    private fun viewModel() = SendToTvViewModel(fake, receivedWaitMs = 100, finalWaitMs = 300, refreshMs = 1_000)

    private fun answer(result: String) = PlayAnswer("r1", result, "Living Room")

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.started(vm: SendToTvViewModel = viewModel()): SendToTvViewModel {
        vm.send(item)
        runCurrent()
        return vm
    }

    @Test
    fun `with exactly one open TV it sends at once and says so`() =
        runTest(dispatcher) {
            started()

            assertEquals(listOf("tv" to fake.buildResult), fake.sent)
            assertEquals(R.string.send_to_tv_sending to listOf<Any>("Living Room"), fake.notices.single())
        }

    @Test
    fun `no received within the wait says couldn't reach and closes the request`() =
        runTest(dispatcher) {
            started()

            advanceTimeBy(150)
            runCurrent()

            assertEquals(listOf(R.string.send_to_tv_sending, R.string.send_to_tv_unreachable), fake.notices.map { it.first })
            assertEquals(listOf(fake.buildResult), fake.closed)
        }

    @Test
    fun `a final answer without received shows its message and never closes`() =
        runTest(dispatcher) {
            started()
            fake.outgoing.emit(answer(PlayAnswer.PLAYING))
            runCurrent()

            assertEquals(R.string.send_to_tv_playing_on, fake.notices.last().first)
            assertTrue(fake.closed.isEmpty())
            assertEquals(listOf("r1"), fake.stopped)
        }

    @Test
    fun `received then playing shows one result`() =
        runTest(dispatcher) {
            started()
            fake.outgoing.emit(answer(PlayAnswer.RECEIVED))
            runCurrent()
            advanceTimeBy(200)
            runCurrent()
            fake.outgoing.emit(answer(PlayAnswer.PLAYING))
            runCurrent()

            assertEquals(listOf(R.string.send_to_tv_sending, R.string.send_to_tv_playing_on), fake.notices.map { it.first })
        }

    @Test
    fun `received without a final answer says couldn't reach`() =
        runTest(dispatcher) {
            started()
            fake.outgoing.emit(answer(PlayAnswer.RECEIVED))
            runCurrent()
            advanceTimeBy(400)
            runCurrent()

            assertEquals(R.string.send_to_tv_unreachable, fake.notices.last().first)
            assertEquals(1, fake.closed.size)
        }

    @Test
    fun `each answer has its message`() =
        runTest(dispatcher) {
            val results =
                mapOf(
                    PlayAnswer.DISMISSED to R.string.send_to_tv_dismissed,
                    PlayAnswer.BUSY to R.string.send_to_tv_busy,
                    PlayAnswer.NO_SOURCE to R.string.send_to_tv_no_source,
                    PlayAnswer.NO_PROFILE to R.string.send_to_tv_no_profile,
                    PlayAnswer.NOT_ALLOWED to R.string.send_to_tv_not_allowed,
                )
            results.forEach { (result, message) ->
                fake.notices.clear()
                started()
                fake.outgoing.emit(answer(result))
                runCurrent()
                assertEquals(result, message, fake.notices.last().first)
            }
        }

    @Test
    fun `an answer for another request is ignored`() =
        runTest(dispatcher) {
            started()
            fake.outgoing.emit(PlayAnswer("other", PlayAnswer.PLAYING))
            runCurrent()

            assertEquals(1, fake.notices.size)
        }

    @Test
    fun `with no open TV or several the picker opens`() =
        runTest(dispatcher) {
            fake.rowList = listOf(TvRow("a", "A", TvStatus.OPEN), TvRow("b", "B", TvStatus.OPEN), TvRow("c", "C", TvStatus.NOT_OPEN))
            val vm = started()

            assertNotNull(vm.sheet.value)
            assertTrue(fake.sent.isEmpty())
            vm.closeSheet()
        }

    @Test
    fun `the picker opens with no open TV`() =
        runTest(dispatcher) {
            fake.rowList = listOf(TvRow("c", "C", TvStatus.NOT_OPEN))
            val vm = started()

            assertNotNull(vm.sheet.value)
            vm.closeSheet()
        }

    @Test
    fun `a picked open TV gets the item, a closed row cannot be picked`() =
        runTest(dispatcher) {
            val open = TvRow("a", "A", TvStatus.OPEN)
            val notOpen = TvRow("c", "C", TvStatus.NOT_OPEN)
            fake.rowList = listOf(open, notOpen, TvRow("d", "D", TvStatus.OPEN))
            val vm = started()

            vm.pick(notOpen)
            runCurrent()
            assertTrue(fake.sent.isEmpty())

            vm.pick(open)
            runCurrent()
            assertEquals("a", fake.sent.single().first)
            assertNull(vm.sheet.value)
        }

    @Test
    fun `the picker refreshes its rows while it shows, and stops when closed`() =
        runTest(dispatcher) {
            fake.rowList = listOf(TvRow("c", "C", TvStatus.NOT_OPEN))
            val vm = started()

            fake.rowList = listOf(TvRow("c", "C", TvStatus.OPEN))
            advanceTimeBy(1_100)
            runCurrent()
            assertEquals(
                TvStatus.OPEN,
                vm.sheet.value
                    ?.rows
                    ?.single()
                    ?.status,
            )

            vm.closeSheet()
            assertNull(vm.sheet.value)
        }

    @Test
    fun `from the player the phone stops first and sends its position`() =
        runTest(dispatcher) {
            var stopped = false
            val vm = viewModel()

            vm.send(item) {
                stopped = true
                2_530_000L
            }
            runCurrent()

            assertTrue(stopped)
            assertEquals(2_530_000L, fake.positionPassed)
        }

    @Test
    fun `from the player nothing stops before a TV is picked`() =
        runTest(dispatcher) {
            var stopped = false
            fake.rowList = listOf(TvRow("a", "A", TvStatus.NOT_OPEN))
            val vm = viewModel()

            vm.send(item) {
                stopped = true
                1L
            }
            runCurrent()

            assertNotNull(vm.sheet.value)
            assertTrue(!stopped)
            vm.closeSheet()
        }

    @Test
    fun `a source without a key says the TV doesn't have it`() =
        runTest(dispatcher) {
            fake.buildResult = null
            started()

            assertEquals(R.string.send_to_tv_no_source, fake.notices.single().first)
            assertTrue(fake.sent.isEmpty())
        }

    private companion object {
        fun request(id: String) =
            SyncPayloads.PlayRequest(
                requestId = id,
                profileId = "anna",
                providerKey = "pk",
                streamId = "42",
                profileName = "Anna",
                sentAt = 0,
            )
    }
}
