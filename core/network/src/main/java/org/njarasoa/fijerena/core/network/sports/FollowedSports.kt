package org.njarasoa.fijerena.core.network.sports

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.njarasoa.fijerena.core.network.xtream.db.FollowedSportEntity
import org.njarasoa.fijerena.core.network.xtream.db.XtreamDatabase

/**
 * The teams and leagues each profile follows (`followed_sport`), synced to the profile's other
 * devices. Only these are fetched for the Sports section, the Home row and the reminders. See the
 * sports teams plan → Followed teams and leagues.
 *
 * Following an item again refreshes its names and logo and keeps when it was first followed.
 * A row whose kind or source this build does not know (written by a newer build) is kept but left
 * out of [observe].
 */
class FollowedSports internal constructor(
    private val db: XtreamDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** What a profile follows, each list in the order it was followed. */
    data class Followed(
        val teams: List<SportsTeam> = emptyList(),
        val leagues: List<SportsLeague> = emptyList(),
    ) {
        val isEmpty: Boolean get() = teams.isEmpty() && leagues.isEmpty()
    }

    private val dao get() = db.followedSportDao()

    /** What [profileId] follows; emits again on every change, local or received through sync. */
    fun observe(profileId: String): Flow<Followed> = dao.observe(profileId).map(::toFollowed)

    /** What [profileId] follows now. */
    suspend fun get(profileId: String): Followed = toFollowed(dao.getAll(profileId))

    suspend fun follow(
        profileId: String,
        team: SportsTeam,
    ) = follow(
        FollowedSportEntity(
            profileId = profileId,
            kind = FollowKind.TEAM.id,
            source = team.source.id,
            itemId = team.id,
            league = team.league,
            name = team.name,
            shortName = team.shortName,
            altNames = FollowedSportEntity.encodeAltNames(team.altNames),
            sport = team.sport,
            logoUrl = team.logoUrl,
            createdAt = 0,
        ),
    )

    suspend fun follow(
        profileId: String,
        league: SportsLeague,
    ) = follow(
        FollowedSportEntity(
            profileId = profileId,
            kind = FollowKind.LEAGUE.id,
            source = league.source.id,
            itemId = league.id,
            name = league.name,
            sport = league.sport,
            logoUrl = league.logoUrl,
            createdAt = 0,
        ),
    )

    private suspend fun follow(entity: FollowedSportEntity) {
        val createdAt = dao.get(entity.profileId, entity.kind, entity.source, entity.itemId)?.createdAt ?: now()
        dao.upsertClearingTombstone(entity.copy(createdAt = createdAt))
    }

    /** Stops following an item, recording it for live sync. */
    suspend fun unfollow(
        profileId: String,
        kind: FollowKind,
        source: SportsSource,
        itemId: String,
    ) = dao.deleteRecordingTombstone(profileId, kind.id, source.id, itemId)

    suspend fun unfollow(
        profileId: String,
        team: SportsTeam,
    ) = unfollow(profileId, FollowKind.TEAM, team.source, team.id)

    suspend fun unfollow(
        profileId: String,
        league: SportsLeague,
    ) = unfollow(profileId, FollowKind.LEAGUE, league.source, league.id)

    suspend fun isFollowed(
        profileId: String,
        kind: FollowKind,
        source: SportsSource,
        itemId: String,
    ): Boolean = dao.isFollowed(profileId, kind.id, source.id, itemId)

    suspend fun isFollowed(
        profileId: String,
        team: SportsTeam,
    ): Boolean = isFollowed(profileId, FollowKind.TEAM, team.source, team.id)

    suspend fun isFollowed(
        profileId: String,
        league: SportsLeague,
    ): Boolean = isFollowed(profileId, FollowKind.LEAGUE, league.source, league.id)

    companion object {
        @Volatile
        private var instance: FollowedSports? = null

        fun getInstance(context: Context): FollowedSports =
            instance ?: synchronized(this) {
                instance ?: FollowedSports(XtreamDatabase.getInstance(context.applicationContext)).also { instance = it }
            }

        internal fun toFollowed(rows: List<FollowedSportEntity>): Followed {
            val teams = mutableListOf<SportsTeam>()
            val leagues = mutableListOf<SportsLeague>()
            for (row in rows) {
                val source = SportsSource.byId(row.source) ?: continue
                when (FollowKind.byId(row.kind)) {
                    FollowKind.TEAM -> {
                        teams +=
                            SportsTeam(
                                source = source,
                                id = row.itemId,
                                name = row.name,
                                shortName = row.shortName,
                                altNames = FollowedSportEntity.decodeAltNames(row.altNames),
                                sport = row.sport,
                                league = row.league,
                                logoUrl = row.logoUrl,
                            )
                    }

                    FollowKind.LEAGUE -> {
                        leagues += SportsLeague(source, row.itemId, row.name, row.sport, row.logoUrl)
                    }

                    // A kind from a newer build: kept in the table, not shown.
                    null -> {}
                }
            }
            return Followed(teams, leagues)
        }
    }
}
