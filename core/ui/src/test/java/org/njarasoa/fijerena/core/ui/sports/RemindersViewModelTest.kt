package org.njarasoa.fijerena.core.ui.sports

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.sports.GameReminders
import org.njarasoa.fijerena.core.network.sports.ReminderChannel
import org.njarasoa.fijerena.core.network.sports.SportsSource

/** [RemindersViewModel]: every action goes through [GameReminders], so the alarms follow (the Reminders page plan). */
@OptIn(ExperimentalCoroutinesApi::class)
class RemindersViewModelTest {
    private val reminders = mockk<GameReminders>(relaxed = true)
    private val settings = mockk<AppSettings>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { settings.observeReminderLeadMinutes() } returns flowOf(10)
        every { settings.reminderLeadMinutes } returns 10
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = RemindersViewModel(reminders, flowOf(emptyList()), settings, "p1", teamGames = { emptyList() })

    private val game = ReminderItem.Game(SportsSource.ESPN, "g1", "Arsenal", "Chelsea", "Premier League", 100, null, fromTeamRule = true)

    @Test
    fun `Remove clears a game through its bell, which mutes a team rule's game`() {
        viewModel().remove(game)

        coVerify(timeout = TIMEOUT_MS) { reminders.clear("p1", SportsSource.ESPN, "g1") }
    }

    @Test
    fun `Remove clears a programme's reminder`() {
        viewModel().remove(ReminderItem.Programme("1:7:150", "News", ReminderChannel(1, 7, "news", "BBC One"), 150))

        coVerify(timeout = TIMEOUT_MS) { reminders.clearProgramme("p1", "1:7:150") }
    }

    @Test
    fun `Stop clears a team's every game`() {
        viewModel().stop(TeamRuleItem(SportsSource.ESPN, "soccer/359", "Arsenal", "Premier League", null, 400, 2))

        coVerify(timeout = TIMEOUT_MS) { reminders.clearForTeam("p1", SportsSource.ESPN, "soccer/359") }
    }

    @Test
    fun `Clear All and a new reminder time go through the reminders`() {
        val viewModel = viewModel()
        viewModel.clearAll()
        viewModel.setLeadMinutes(30)

        coVerify(timeout = TIMEOUT_MS) { reminders.clearAll("p1") }
        coVerify(timeout = TIMEOUT_MS) { reminders.setLeadMinutes(settings, 30) }
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
