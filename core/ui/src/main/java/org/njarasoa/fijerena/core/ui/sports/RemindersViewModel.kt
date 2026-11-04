package org.njarasoa.fijerena.core.ui.sports

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.sports.GameReminders
import org.njarasoa.fijerena.core.network.sports.ReminderChannel
import org.njarasoa.fijerena.core.network.sports.ReminderSchedule
import org.njarasoa.fijerena.core.network.sports.ReminderTeam
import org.njarasoa.fijerena.core.network.sports.SportsData
import org.njarasoa.fijerena.core.network.sports.SportsGame
import org.njarasoa.fijerena.core.network.sports.SportsSource
import org.njarasoa.fijerena.core.network.sports.SportsTeam
import org.njarasoa.fijerena.core.network.xtream.db.GameReminderEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase
import org.njarasoa.fijerena.core.ui.utils.launchGuarded

/** One upcoming reminder on the Reminders page (the Reminders page plan → Design V). */
sealed interface ReminderItem {
    val startEpochSec: Long

    /** A game's reminder; [fromTeamRule]: set by a team's "every game", so Remove mutes it. */
    data class Game(
        val source: SportsSource,
        val gameId: String,
        val homeName: String,
        val awayName: String?,
        val competitionName: String,
        override val startEpochSec: Long,
        /** The team whose schedule has the game: Open reads it to find the game. */
        val team: SportsTeam?,
        val fromTeamRule: Boolean,
    ) : ReminderItem

    /** A guide programme's reminder (Search the Guide's Remind Me), on [channel]. */
    data class Programme(
        val id: String,
        val title: String,
        val channel: ReminderChannel,
        override val startEpochSec: Long,
    ) : ReminderItem
}

/** A team's "every game" on the Reminders page: its next game and how many coming games it has. */
data class TeamRuleItem(
    val source: SportsSource,
    val teamId: String,
    val teamName: String,
    val leagueName: String,
    val team: SportsTeam?,
    val nextStartEpochSec: Long?,
    val gameCount: Int,
)

sealed interface RemindersState {
    data object Loading : RemindersState

    data class Loaded(
        /** Games and programmes, the soonest first. */
        val upcoming: List<ReminderItem>,
        /** Team rules, by team name. */
        val teams: List<TeamRuleItem>,
    ) : RemindersState {
        val isEmpty: Boolean get() = upcoming.isEmpty() && teams.isEmpty()
    }
}

/** The Reminders page from the profile's `game_reminder` rows. Pure, so it is unit tested. */
object RemindersPage {
    fun build(rows: List<GameReminderEntity>): RemindersState.Loaded {
        // A muted game (start stored negative) only keeps a team rule from adding it again.
        val scheduled = rows.filter { it.startEpoch > 0 }
        val upcoming =
            scheduled
                .mapNotNull { row ->
                    if (row.source == GameReminders.PROGRAMME_SOURCE) {
                        ReminderChannel.decode(row.lookupTeam)?.let { ReminderItem.Programme(row.gameId, row.homeName, it, row.startEpoch) }
                    } else {
                        SportsSource.byId(row.source)?.let { source ->
                            ReminderItem.Game(
                                source = source,
                                gameId = row.gameId,
                                homeName = row.homeName,
                                awayName = row.awayName,
                                competitionName = row.competitionName,
                                startEpochSec = row.startEpoch,
                                team = ReminderTeam.decode(row.lookupTeam)?.toTeam(),
                                fromTeamRule = row.teamKey != null,
                            )
                        }
                    }
                }.sortedBy { it.startEpochSec }
        val teams =
            rows
                .filter { it.isTeamRule }
                .mapNotNull { rule ->
                    val source = SportsSource.byId(rule.source) ?: return@mapNotNull null
                    val games = scheduled.filter { it.teamKey == rule.teamKey }
                    TeamRuleItem(
                        source = source,
                        teamId = rule.gameId.removePrefix(GameReminderEntity.TEAM_RULE_PREFIX),
                        teamName = rule.homeName,
                        leagueName = rule.competitionName,
                        team = ReminderTeam.decode(rule.lookupTeam)?.toTeam(),
                        nextStartEpochSec = games.minOfOrNull { it.startEpoch },
                        gameCount = games.size,
                    )
                }.sortedBy { it.teamName.lowercase() }
        return RemindersState.Loaded(upcoming, teams)
    }

    /** When [item] rings with the device's lead of [leadMinutes] (not before now, as the alarm does). */
    fun ringsAtEpochSec(
        item: ReminderItem,
        leadMinutes: Int,
        nowSec: Long,
    ): Long = maxOf(nowSec, item.startEpochSec - leadMinutes * 60L)
}

/**
 * The Reminders page (the Reminders page plan): the profile's upcoming reminders and team rules
 * on this device, the device's lead time, and the page's actions. Every write goes through
 * [GameReminders], so the alarms follow.
 */
class RemindersViewModel(
    private val reminders: GameReminders,
    rows: Flow<List<GameReminderEntity>>,
    private val settings: AppSettings,
    private val profileId: String,
    private val teamGames: suspend (SportsTeam) -> List<SportsGame>,
) : ViewModel() {
    val state: StateFlow<RemindersState> =
        rows.map(RemindersPage::build).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RemindersState.Loading)

    /** Minutes before the start a reminder rings on this device, one of [leadChoices]. */
    val leadMinutes: StateFlow<Int> =
        settings.observeReminderLeadMinutes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), settings.reminderLeadMinutes)

    val leadChoices: List<Int> = ReminderSchedule.LEAD_CHOICES_MINUTES

    /** Removes [item]: a game set by a team's "every game" is muted, as its bell does. */
    fun remove(item: ReminderItem) =
        launch("remove") {
            when (item) {
                is ReminderItem.Game -> reminders.clear(profileId, item.source, item.gameId)
                is ReminderItem.Programme -> reminders.clearProgramme(profileId, item.id)
            }
        }

    /** Stops [rule]: the team's standing row and the reminders it set go. */
    fun stop(rule: TeamRuleItem) = launch("stop") { reminders.clearForTeam(profileId, rule.source, rule.teamId) }

    fun clearAll() = launch("clearAll") { reminders.clearAll(profileId) }

    fun setLeadMinutes(minutes: Int) = launch("setLeadMinutes") { reminders.setLeadMinutes(settings, minutes) }

    /** The game of [item], read from its team's schedule (cached when fresh); null when it is gone. */
    suspend fun gameOf(item: ReminderItem.Game): SportsGame? =
        item.team?.let { team -> teamGames(team).firstOrNull { it.source == item.source && it.id == item.gameId } }

    private fun launch(
        name: String,
        block: suspend () -> Unit,
    ) {
        viewModelScope.launchGuarded("RemindersViewModel.$name", Dispatchers.IO) { block() }
    }

    class Factory(
        context: Context,
    ) : ViewModelProvider.Factory {
        private val app = context.applicationContext

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(RemindersViewModel::class.java)) { "Unknown ViewModel class: ${modelClass.name}" }
            val settings = AppSettings(app)
            val profileId = settings.activeProfileId
            return RemindersViewModel(
                reminders = GameReminders.getInstance(app),
                rows = XtreamDatabase.getInstance(app).gameReminderDao().observeAll(profileId),
                settings = settings,
                profileId = profileId,
                teamGames = { team -> SportsData.getInstance(app).teamGames(team) },
            ) as T
        }
    }
}
