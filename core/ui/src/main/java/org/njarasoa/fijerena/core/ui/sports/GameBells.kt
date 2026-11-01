package org.njarasoa.fijerena.core.ui.sports

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.sports.GameReminders
import org.njarasoa.fijerena.core.network.sports.GameState
import org.njarasoa.fijerena.core.network.sports.ProgrammeReminder
import org.njarasoa.fijerena.core.network.sports.SportsGame
import org.njarasoa.fijerena.core.network.sports.SportsTeam
import org.njarasoa.fijerena.core.ui.utils.launchGuarded

// The reminder bells on games and on a team's page (the sports teams plan → Reminders, SP-D12).

/** Where the bells read and write reminders: [GameReminders] for the profile in use. A fake in the tests. */
interface GameBellStore {
    /** What the profile has reminders for; emits again on every change. */
    fun reminded(): Flow<GameReminders.Reminded>

    suspend fun set(game: SportsGame)

    suspend fun clear(game: SportsGame)

    suspend fun setForTeam(team: SportsTeam)

    suspend fun clearForTeam(team: SportsTeam)

    suspend fun setProgramme(programme: ProgrammeReminder) {}
}

/** The real [GameBellStore]: [GameReminders] for the profile in use. */
class AppGameBellStore(
    context: Context,
) : GameBellStore {
    private val app = context.applicationContext
    private val reminders get() = GameReminders.getInstance(app)
    private val profileId get() = AppSettings(app).activeProfileId

    override fun reminded(): Flow<GameReminders.Reminded> = reminders.observe(profileId)

    override suspend fun set(game: SportsGame) {
        reminders.set(profileId, game)
    }

    override suspend fun clear(game: SportsGame) = reminders.clear(profileId, game)

    override suspend fun setForTeam(team: SportsTeam) = reminders.setForTeam(profileId, team)

    override suspend fun clearForTeam(team: SportsTeam) = reminders.clearForTeam(profileId, team)

    override suspend fun setProgramme(programme: ProgrammeReminder) {
        reminders.setProgramme(profileId, programme)
    }
}

/** The store the bells use; null: the app's ([AppGameBellStore]). The tests give a fake. */
val LocalGameBellStore = staticCompositionLocalOf<GameBellStore?> { null }

/**
 * A screen's bells: what has a reminder ([reminded]), and the bell's tap for a game ([toggle]) or
 * for every game of a team ([toggleTeam]); a guide programme's Remind Me ([remindProgramme]). Setting one asks for the notification permission first
 * when the app does not have it ([rememberGameReminderPermission]).
 */
@Stable
class GameBells internal constructor(
    val reminded: GameReminders.Reminded,
    private val onToggle: (SportsGame) -> Unit,
    private val onToggleTeam: (SportsTeam) -> Unit,
    private val onRemindProgramme: (ProgrammeReminder) -> Unit = {},
) {
    fun toggle(game: SportsGame) = onToggle(game)

    fun toggleTeam(team: SportsTeam) = onToggleTeam(team)

    fun remindProgramme(programme: ProgrammeReminder) = onRemindProgramme(programme)
}

/** Whether [game] may have a bell at [nowSec]: it has not started, and is not postponed, cancelled or over. */
fun canRemind(
    game: SportsGame,
    nowSec: Long,
): Boolean = game.state == GameState.SCHEDULED && game.startEpochSec > nowSec

@Composable
fun rememberGameBells(): GameBells {
    val context = LocalContext.current
    val given = LocalGameBellStore.current
    val store = remember(given) { given ?: AppGameBellStore(context) }
    val reminded by remember(store) { store.reminded() }.collectAsStateWithLifecycle(initialValue = GameReminders.Reminded())
    val askNotifications = rememberGameReminderPermission()
    val scope = rememberCoroutineScope()
    return remember(store, reminded, askNotifications) {
        GameBells(
            reminded = reminded,
            onToggle = { game ->
                val on = reminded.has(game)
                if (!on) askNotifications()
                scope.launchGuarded("GameBells.toggle") { if (on) store.clear(game) else store.set(game) }
            },
            onToggleTeam = { team ->
                val on = reminded.hasTeam(team)
                if (!on) askNotifications()
                scope.launchGuarded("GameBells.toggleTeam") { if (on) store.clearForTeam(team) else store.setForTeam(team) }
            },
            onRemindProgramme = { programme ->
                askNotifications()
                scope.launchGuarded("GameBells.remindProgramme") { store.setProgramme(programme) }
            },
        )
    }
}
