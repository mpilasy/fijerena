package org.njarasoa.fijerena.core.network.sports

import java.time.Instant

/**
 * Guide samples for the game matcher. The titles, descriptions, channel ids and times are copied
 * from public XMLTV guides of 2026-10-30 to 2026-11-02 (UK, FR, DE, ZA and US sports guides) unless
 * a comment says otherwise.
 */
internal object GameChannelFixtures {
    fun epoch(iso: String): Long = Instant.parse(iso).epochSecond

    fun programme(
        channelId: String,
        channelName: String,
        title: String,
        start: String,
        end: String,
        description: String? = null,
        category: String? = "Sports",
        providerId: Long = 1L,
    ) = GuideProgramme(providerId, channelId, channelName, title, description, category, epoch(start), epoch(end))

    fun channel(
        streamId: Int,
        name: String,
        epgChannelId: String? = null,
        providerId: Long = 1L,
        hidden: Boolean = false,
    ) = LiveChannel(providerId, streamId, name, epgChannelId, categoryId = "sports", hidden = hidden)

    fun team(
        name: String,
        vararg altNames: String,
        shortName: String? = null,
        sport: String = "soccer",
    ) = SportsTeam(SportsSource.ESPN, name.lowercase(), name, shortName, altNames.toList(), sport)

    fun game(
        start: String,
        home: SportsTeam,
        away: SportsTeam?,
        competition: String,
        vararg broadcasters: Broadcaster,
    ) = SportsGame(
        source = SportsSource.ESPN,
        id = "${home.id}-${away?.id}",
        startEpochSec = epoch(start),
        home = home,
        away = away,
        competition = SportsLeague(SportsSource.ESPN, competition, competition, home.sport),
        broadcasters = broadcasters.toList(),
    )

    fun broadcaster(
        name: String,
        country: String?,
    ) = Broadcaster(name, country, from = BroadcasterOrigin.ESPN)

    /**
     * A guide that answers as the index does: programmes that start in the window, on the asked
     * channels, or whose title or description has one of the terms as whole words.
     */
    class FakeGuide(
        private val programmes: List<GuideProgramme>,
    ) : GuideLookup {
        val searched = mutableListOf<Collection<String>>()

        override suspend fun programmesOnChannels(
            epgChannelIds: Collection<String>,
            fromSec: Long,
            toSec: Long,
        ): List<GuideProgramme> = programmes.filter { it.startSec in fromSec..toSec && it.channelId in epgChannelIds }

        override suspend fun searchTitles(
            terms: Collection<String>,
            fromSec: Long,
            toSec: Long,
        ): List<GuideProgramme> {
            searched += terms
            return programmes.filter { p ->
                val text = SportsText.padded("${p.title} ${p.description.orEmpty()}")
                p.startSec in fromSec..toSec && terms.any { SportsText.has(text, SportsText.fold(it)) }
            }
        }
    }
}
