package org.njarasoa.fijerena.core.ui.person

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.tmdb.PersonRole
import org.njarasoa.fijerena.core.network.tmdb.PersonRows
import org.njarasoa.fijerena.core.network.tmdb.ShowAppearance
import org.njarasoa.fijerena.core.network.tmdb.ShowTarget
import org.njarasoa.fijerena.core.network.tmdb.TmdbCombinedCredits
import org.njarasoa.fijerena.core.network.tmdb.TmdbCredit
import org.njarasoa.fijerena.core.network.tmdb.TmdbCreditEpisode
import org.njarasoa.fijerena.core.network.tmdb.TmdbCreditMedia
import org.njarasoa.fijerena.core.network.tmdb.TmdbCreditSeason
import org.njarasoa.fijerena.core.network.tmdb.TmdbPerson
import org.njarasoa.fijerena.core.network.tmdb.TmdbPersonCredit
import org.njarasoa.fijerena.core.network.tmdb.showAppearances
import org.njarasoa.fijerena.core.player.domain.MediaItem
import org.njarasoa.fijerena.core.player.domain.MediaType
import java.util.Collections
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * A person's page (cast tab plan, Phase 3): each row's switch for missing titles starts off, flips
 * only its own row (UX-D7) and is not for a child, the crew flag reaches the matching, a failed or impossible TMDB call is an error, and a
 * catalogue sync makes the rows again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PersonViewModelTest {
    private class RowsCall(
        val crew: Boolean,
        val missing: PersonViewModel.ShowMissing,
    )

    private class FakeData(
        override val hasKey: Boolean = true,
        override val child: Boolean = false,
        override val devMode: Boolean = false,
        val failure: Exception? = null,
    ) : PersonViewModel.Data {
        val updates = MutableStateFlow(0L)

        /** The thread of each catalogue update that reaches the page. */
        val updateThreads: MutableList<String> = Collections.synchronizedList(mutableListOf())
        override val catalogueUpdates = updates.onEach { updateThreads += Thread.currentThread().name }
        val calls: MutableList<RowsCall> = Collections.synchronizedList(mutableListOf())

        override suspend fun person(personId: Int): TmdbPerson {
            failure?.let { throw it }
            return PERSON
        }

        override suspend fun rows(
            person: TmdbPerson,
            crew: Boolean,
            missing: PersonViewModel.ShowMissing,
        ): PersonRows {
            calls += RowsCall(crew, missing)
            return PersonRows(movies = listOf(item("m${calls.size}")), shows = emptyList())
        }

        /** What the credit call gives; a failure when null. */
        var lines: PersonViewModel.ShowLines? = null
        val creditCalls: MutableList<String> = Collections.synchronizedList(mutableListOf())

        override suspend fun appearances(
            personId: Int,
            creditId: String,
            show: MediaItem,
        ): PersonViewModel.ShowLines {
            creditCalls += creditId
            return lines ?: error("HTTP 500")
        }
    }

    private val created = mutableListOf<PersonViewModel>()
    private var mainThread: ExecutorService? = null

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        created.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
        mainThread?.shutdown()
    }

    private fun page(
        data: FakeData,
        personId: Int? = 7,
        crew: Boolean = false,
    ) = PersonViewModel(personId, crew, data).also { created += it }

    private fun <T> await(read: suspend () -> T): T = runBlocking { withTimeout(TIMEOUT_MS) { read() } }

    private fun PersonViewModel.loaded(predicate: (PersonViewModel.State.Loaded) -> Boolean = { it.rows != null }) =
        await { state.first { it is PersonViewModel.State.Loaded && predicate(it) } as PersonViewModel.State.Loaded }

    private fun PersonViewModel.error() = await { state.first { it is PersonViewModel.State.Error } as PersonViewModel.State.Error }

    @Test
    fun `both switches start off and the rows have only the library's titles`() {
        val data = FakeData()
        val vm = page(data)

        val state = vm.loaded()

        assertTrue(vm.canShowMissing)
        assertEquals(PersonViewModel.ShowMissing(), vm.showMissing.value)
        assertEquals(PersonViewModel.ShowMissing(movies = false, shows = false), data.calls.single().missing)
        assertTrue(state.hasMovies)
        assertFalse(state.hasShows)
    }

    @Test
    fun `the Movies switch on makes the rows again with the missing films only`() {
        val data = FakeData()
        val vm = page(data)
        vm.loaded()

        vm.setShowMissing(PersonViewModel.Row.MOVIES, true)
        vm.loaded {
            it.rows
                ?.movies
                ?.first()
                ?.id == "m2"
        }

        assertEquals(PersonViewModel.ShowMissing(movies = true, shows = false), vm.showMissing.value)
        assertEquals(PersonViewModel.ShowMissing(movies = true, shows = false), data.calls.last().missing)
    }

    @Test
    fun `each row keeps its own switch`() {
        val data = FakeData()
        val vm = page(data)
        vm.loaded()

        vm.setShowMissing(PersonViewModel.Row.SHOWS, true)
        vm.loaded {
            it.rows
                ?.movies
                ?.first()
                ?.id == "m2"
        }
        vm.setShowMissing(PersonViewModel.Row.MOVIES, true)
        vm.loaded {
            it.rows
                ?.movies
                ?.first()
                ?.id == "m3"
        }
        vm.setShowMissing(PersonViewModel.Row.SHOWS, false)
        vm.loaded {
            it.rows
                ?.movies
                ?.first()
                ?.id == "m4"
        }

        assertEquals(
            listOf(
                PersonViewModel.ShowMissing(),
                PersonViewModel.ShowMissing(shows = true),
                PersonViewModel.ShowMissing(movies = true, shows = true),
                PersonViewModel.ShowMissing(movies = true),
            ),
            data.calls.map { it.missing },
        )
        assertTrue(vm.showMissing.value[PersonViewModel.Row.MOVIES])
        assertFalse(vm.showMissing.value[PersonViewModel.Row.SHOWS])
    }

    @Test
    fun `a switch set to the state it has makes no new rows`() {
        val data = FakeData()
        val vm = page(data)
        vm.loaded()

        vm.setShowMissing(PersonViewModel.Row.MOVIES, false)

        assertEquals(1, data.calls.size)
    }

    @Test
    fun `a child gets no switch`() {
        val data = FakeData(child = true)
        val vm = page(data)
        vm.loaded()

        vm.setShowMissing(PersonViewModel.Row.MOVIES, true)
        vm.setShowMissing(PersonViewModel.Row.SHOWS, true)

        assertFalse(vm.canShowMissing)
        assertEquals(PersonViewModel.ShowMissing(), vm.showMissing.value)
        assertEquals(1, data.calls.size)
    }

    @Test
    fun `a person opened from a crew line is matched on their crew credits`() {
        val data = FakeData()
        val vm = page(data, crew = true)

        vm.loaded()

        assertTrue(data.calls.single().crew)
    }

    @Test
    fun `a failed TMDB call is an error without its raw text`() {
        val vm = page(FakeData(failure = IllegalStateException("HTTP 500")))

        assertNull(vm.error().raw)
    }

    @Test
    fun `in Expert mode the error has its raw text`() {
        val vm = page(FakeData(devMode = true, failure = IllegalStateException("HTTP 500")))

        assertEquals("HTTP 500", vm.error().raw)
    }

    @Test
    fun `no TMDB key or no person id is an error`() {
        page(FakeData(hasKey = false)).error()
        page(FakeData(), personId = null).error()
    }

    @Test
    fun `a catalogue sync makes the rows again`() {
        val data = FakeData()
        val vm = page(data)
        vm.loaded()

        data.updates.value = 1
        vm.loaded {
            it.rows
                ?.movies
                ?.first()
                ?.id == "m2"
        }

        assertEquals(2, data.calls.size)
    }

    @Test
    fun `a catalogue sync makes the rows on Main, as the switch does`() {
        val main = Executors.newSingleThreadExecutor { Thread(it, "test-main") }.also { mainThread = it }
        Dispatchers.setMain(main.asCoroutineDispatcher())
        val data = FakeData()
        val vm = page(data)
        vm.loaded()

        data.updates.value = 1
        vm.loaded {
            it.rows
                ?.movies
                ?.first()
                ?.id == "m2"
        }

        // One thread only replaces the rows job, so a quick switch and sync cannot lose one load.
        assertEquals(listOf("test-main", "test-main"), data.updateThreads.map { it.substringBefore(" @") })
    }

    private fun PersonViewModel.opening(predicate: (PersonViewModel.ShowOpening) -> Boolean) =
        await { showOpening.first { it != null && predicate(it) }!! }

    @Test
    fun `a one-episode role opens that episode at once, after the credit call`() {
        val data = FakeData()
        data.lines = PersonViewModel.ShowLines(listOf(ShowAppearance(9, 17, "Authority", "e917")), setOf(9))
        val vm = page(data)
        vm.loaded()

        vm.openShow(SHOW, PersonRole(character = "Merritt Rook", episodeCount = 1, creditId = "c1"))

        assertEquals(
            PersonViewModel.ShowOpening.Open(SHOW, ShowTarget(episodeId = "e917", season = 9)),
            vm.opening { it is PersonViewModel.ShowOpening.Open },
        )
        assertEquals(listOf("c1"), data.creditCalls)
        vm.opened()
        assertNull(vm.showOpening.value)
    }

    @Test
    fun `a role of several episodes lists them until the list is closed`() {
        val data = FakeData()
        val lines = listOf(ShowAppearance(1, 1, "One", "e11"), ShowAppearance(2, 5, "Five", null, inSource = false))
        data.lines = PersonViewModel.ShowLines(lines, setOf(1))
        val vm = page(data)
        vm.loaded()

        vm.openShow(SHOW, PersonRole(character = "Cop", episodeCount = 2, creditId = "c2"))

        assertEquals(
            PersonViewModel.ShowOpening.Lines(SHOW, lines, setOf(1)),
            vm.opening { it is PersonViewModel.ShowOpening.Lines },
        )
        vm.closeShow()
        assertNull(vm.showOpening.value)
    }

    @Test
    fun `a show card without a credit, on a crew page or with a failed credit call opens the show`() {
        val plain = PersonViewModel.ShowOpening.Open(SHOW, ShowTarget())
        val noCredit = page(FakeData()).also { it.loaded() }
        noCredit.openShow(SHOW, PersonRole(character = "Cop"))
        assertEquals(plain, noCredit.showOpening.value)

        val crewData = FakeData()
        val crewPage = page(crewData, crew = true).also { it.loaded() }
        crewPage.openShow(SHOW, PersonRole(creditId = "c3"))
        assertEquals(plain, crewPage.showOpening.value)
        assertTrue(crewData.creditCalls.isEmpty())

        val failing = page(FakeData()).also { it.loaded() }
        failing.openShow(SHOW, PersonRole(character = "Cop", creditId = "c4"))
        assertEquals(plain, failing.opening { it is PersonViewModel.ShowOpening.Open })
    }

    @Test
    fun `where a show card goes once its lines are known`() {
        fun of(vararg lines: ShowAppearance) = PersonViewModel.showOpeningOf(SHOW, PersonViewModel.ShowLines(lines.toList(), setOf(3)))

        // No line: the show as it opens.
        assertEquals(PersonViewModel.ShowOpening.Open(SHOW, ShowTarget()), of())
        // One season of a regular role: that season.
        assertEquals(PersonViewModel.ShowOpening.Open(SHOW, ShowTarget(season = 3)), of(ShowAppearance(3)))
        // One episode the source lacks: its season, with the note (EC-D3).
        val lacking = ShowAppearance(3, 4, "Four", null, inSource = false)
        assertEquals(PersonViewModel.ShowOpening.Open(SHOW, ShowTarget(season = 3, missing = lacking)), of(lacking))
        // Several: the list.
        assertTrue(of(ShowAppearance(3), ShowAppearance(4, inSource = false)) is PersonViewModel.ShowOpening.Lines)
    }

    @Test
    fun `a regular of one season with a special gets the list, not the special at once`() {
        // A regular of season 3 whom TMDB names in one special only: the lines are the season, then
        // the special, so the card opens the list (it opened the special, the walk of 2026-10-30).
        val credit =
            TmdbCredit(
                media =
                    TmdbCreditMedia(
                        episodes = listOf(TmdbCreditEpisode(0, 2, "Wedding Day")),
                        seasons = listOf(TmdbCreditSeason(3, 13)),
                    ),
            )
        val lines = showAppearances(credit, mapOf((3 to 1) to "e31"))

        val opening = PersonViewModel.showOpeningOf(SHOW, PersonViewModel.ShowLines(lines, setOf(3)))

        assertEquals(PersonViewModel.ShowOpening.Lines(SHOW, lines, setOf(3)), opening)
        assertEquals(listOf(ShowAppearance(3), ShowAppearance(0, 2, "Wedding Day", null, inSource = false)), lines)
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L

        val SHOW = MediaItem(id = "s1", name = "SVU", mediaType = MediaType.SERIES, categoryId = "", providerId = 3L)

        val PERSON =
            TmdbPerson(
                id = 7,
                name = "Ana",
                combinedCredits = TmdbCombinedCredits(cast = listOf(TmdbPersonCredit(id = 1, mediaType = "movie", title = "Film"))),
            )

        fun item(id: String) = MediaItem(id = id, name = id, mediaType = MediaType.MOVIE, categoryId = "")
    }
}
