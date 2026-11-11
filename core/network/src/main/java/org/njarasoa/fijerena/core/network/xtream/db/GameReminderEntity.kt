package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Entity
import androidx.room.Index

/**
 * A game reminder of a profile: the alarm rings the device's lead time before [startEpoch]. Local
 * only, never synced: a reminder rings on the device where it was set. See the sports teams plan →
 * Reminders.
 *
 * [source] is `SportsSource.id` and [gameId] the game id at that source. [homeName], [awayName]
 * (null for an event with one side) and [competitionName] show the reminder without a network
 * call. [lookupTeam] is the team whose schedule has the game, as JSON (`ReminderTeam`): the alarm
 * reads that schedule again when it rings, so a moved game rings at its new time.
 *
 * [teamKey] (`<source>:<teamId>`) is set on the reminders of "remind me of every game of this
 * team". That team's standing row has [gameId] `team:<teamId>` and [startEpoch] 0 ([isTeamRule]):
 * it is never scheduled, and it keeps the choice while the team has no coming game. A game of the
 * team whose bell was cleared stays as a muted row, its start stored negative and no alarm, so the
 * team's next refresh does not add it again.
 *
 * A guide programme's reminder has [source] `guide` (`GameReminders.PROGRAMME_SOURCE`), [gameId]
 * `<providerId>:<streamId>:<start>`, the programme title in [homeName], the channel name in
 * [competitionName] and the channel to tune in [lookupTeam], as JSON (`ReminderChannel`).
 *
 * `GameReminders` in the sports package reads and writes it.
 */
@Entity(
    tableName = "game_reminder",
    primaryKeys = ["profileId", "source", "gameId"],
    indices = [Index("startEpoch")],
)
data class GameReminderEntity(
    val profileId: String,
    val source: String,
    val gameId: String,
    val startEpoch: Long,
    val homeName: String,
    val awayName: String? = null,
    val competitionName: String,
    val teamKey: String? = null,
    val lookupTeam: String,
    val createdAt: Long,
) {
    val isTeamRule: Boolean get() = gameId.startsWith(TEAM_RULE_PREFIX)

    companion object {
        /** The [gameId] prefix of a team's standing row. */
        const val TEAM_RULE_PREFIX = "team:"

        fun teamKey(
            source: String,
            teamId: String,
        ): String = "$source:$teamId"

        fun teamRuleGameId(teamId: String): String = "$TEAM_RULE_PREFIX$teamId"
    }
}
