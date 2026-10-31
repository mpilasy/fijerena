package org.njarasoa.fijerena.core.network.xtream.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** The game reminders ([GameReminderEntity]). Local only: no sync triggers, no tombstones. */
@Dao
interface GameReminderDao {
    /** One profile's rows: game reminders, muted ones and team rows. */
    @Query("SELECT * FROM game_reminder WHERE profileId = :profileId")
    fun observeAll(profileId: String): Flow<List<GameReminderEntity>>

    @Query("SELECT * FROM game_reminder WHERE profileId = :profileId AND source = :source AND gameId = :gameId")
    suspend fun get(
        profileId: String,
        source: String,
        gameId: String,
    ): GameReminderEntity?

    /** Every game reminder of every profile (team rows left out). */
    @Query("SELECT * FROM game_reminder WHERE startEpoch > 0")
    suspend fun allGames(): List<GameReminderEntity>

    /** Every muted game reminder (start stored negative) of every profile. */
    @Query("SELECT * FROM game_reminder WHERE startEpoch < 0")
    suspend fun allMuted(): List<GameReminderEntity>

    /** Every team row of every profile. */
    @Query("SELECT * FROM game_reminder WHERE gameId LIKE '${GameReminderEntity.TEAM_RULE_PREFIX}%'")
    suspend fun allTeamRules(): List<GameReminderEntity>

    /** The game reminders, of every profile, of the games [gameIds] (team rows left out). */
    @Query("SELECT * FROM game_reminder WHERE startEpoch > 0 AND gameId IN (:gameIds)")
    suspend fun forGames(gameIds: List<String>): List<GameReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: GameReminderEntity)

    @Query("UPDATE game_reminder SET startEpoch = :startEpoch WHERE profileId = :profileId AND source = :source AND gameId = :gameId")
    suspend fun updateStart(
        profileId: String,
        source: String,
        gameId: String,
        startEpoch: Long,
    )

    @Query("DELETE FROM game_reminder WHERE profileId = :profileId AND source = :source AND gameId = :gameId")
    suspend fun delete(
        profileId: String,
        source: String,
        gameId: String,
    )

    /** One profile's rows of a team: its game reminders and its team row. */
    @Query("SELECT * FROM game_reminder WHERE profileId = :profileId AND teamKey = :teamKey")
    suspend fun forTeam(
        profileId: String,
        teamKey: String,
    ): List<GameReminderEntity>

    @Query("DELETE FROM game_reminder WHERE profileId = :profileId AND teamKey = :teamKey")
    suspend fun deleteTeam(
        profileId: String,
        teamKey: String,
    )

    /** Profile deletion: all of that profile's reminders. */
    @Query("DELETE FROM game_reminder WHERE profileId = :profileId")
    suspend fun deleteProfile(profileId: String)
}
