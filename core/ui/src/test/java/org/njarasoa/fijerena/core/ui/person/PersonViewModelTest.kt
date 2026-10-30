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
import org.njarasoa.fijerena.core.network.tmdb.PersonRows
import org.njarasoa.fijerena.core.network.tmdb.TmdbCombinedCredits
import org.njarasoa.fijerena.core.network.tmdb.TmdbPerson
import org.njarasoa.fijerena.core.network.tmdb.TmdbPersonCredit
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

    private companion object {
        const val TIMEOUT_MS = 5_000L

        val PERSON =
            TmdbPerson(
                id = 7,
                name = "Ana",
                combinedCredits = TmdbCombinedCredits(cast = listOf(TmdbPersonCredit(id = 1, mediaType = "movie", title = "Film"))),
            )

        fun item(id: String) = MediaItem(id = id, name = id, mediaType = MediaType.MOVIE, categoryId = "")
    }
}
