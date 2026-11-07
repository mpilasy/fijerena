package org.njarasoa.fijerena.core.network.sports

import org.njarasoa.fijerena.core.network.xmltv.EpgChannelMatcher
import org.njarasoa.fijerena.core.network.xtream.db.XtreamStreamEntity

/**
 * A live channel of the profile, as the matcher needs it. [hidden] is a channel the profile can't
 * open (excluded, or in a live category its source hides): it still takes part in matching a guide
 * channel, so that the guide does not match a wrong channel in its place, but it is never returned.
 */
data class LiveChannel(
    val providerId: Long,
    val streamId: Int,
    val name: String,
    val epgChannelId: String?,
    val categoryId: String,
    val iconUrl: String? = null,
    val hidden: Boolean = false,
) {
    companion object {
        /** A live stream row of `xtream_streams`; [hidden] as the guide search decides it. Without its logo when not [withIcon]. */
        fun of(
            stream: XtreamStreamEntity,
            hidden: Boolean = stream.excluded,
            withIcon: Boolean = true,
        ): LiveChannel =
            LiveChannel(
                providerId = stream.providerId,
                streamId = stream.streamId,
                name = stream.name,
                epgChannelId = stream.epgChannelId,
                categoryId = stream.categoryId,
                iconUrl = stream.streamIcon.takeIf { withIcon },
                hidden = hidden,
            )
    }
}

/**
 * A guide programme. [providerId] is the source whose guide source has it (0 when unknown);
 * [channelId] is the guide channel id and [channelName] its display name.
 */
data class GuideProgramme(
    val providerId: Long,
    val channelId: String,
    val channelName: String,
    val title: String,
    val description: String?,
    val category: String?,
    val startSec: Long,
    val endSec: Long,
)

/** What the matcher reads from the guide. Both calls return only programmes that start in [fromSec]..[toSec]. */
interface GuideLookup {
    /** The programmes of the guide channels [epgChannelIds]. */
    suspend fun programmesOnChannels(
        epgChannelIds: Collection<String>,
        fromSec: Long,
        toSec: Long,
    ): List<GuideProgramme>

    /** The programmes whose title or description has one of [terms] (whole words, any case or accent). */
    suspend fun searchTitles(
        terms: Collection<String>,
        fromSec: Long,
        toSec: Long,
    ): List<GuideProgramme>
}

/** How a [GameChannel] was found, best first. */
enum class GameChannelHow {
    /** A guide programme of the game on a channel of one of its broadcasters. */
    GUIDE_ON_BROADCASTER,

    /** A guide programme of the game on any channel of the profile. */
    GUIDE,

    /** A channel whose name names the game (a PPV or event channel: "UK| PPV 3: Bayern v Ajax"). */
    CHANNEL_NAME,

    /** A channel of a broadcaster, with no guide programme for the game ("On <broadcaster>"). */
    BROADCASTER_CHANNEL,
}

/** One way to watch a game: a channel of the profile, its guide programme of the game when known. */
data class GameChannel(
    val stream: LiveChannel,
    val programme: GuideProgramme?,
    val broadcaster: Broadcaster?,
    val how: GameChannelHow,
)

/**
 * Finds the profile's live channels that show a game (see the sports teams plan → "Matching a game
 * to the profile's channels"):
 * 1. a guide programme of the game on a broadcaster's channel;
 * 2. a guide programme of the game on any channel (found by the team names);
 * 3. a channel whose name names the game (PPV and event channels; see [GameText.namesGame] and
 *    [ChannelNameDates]);
 * 4. a broadcaster's channel with no programme.
 * An empty list: the profile's channels and guide do not have the game. The broadcasters (ESPN's,
 * the NHL's, MLB's networks) are used only to find channels with their names. A channel name shows
 * once: the same name in two categories or sources gives only its first channel (the profile's
 * source order).
 *
 * A programme is of the game when it starts from [BEFORE_SEC] before to [AFTER_SEC] after the
 * game, or is on at the start after starting at most [ON_AIR_BEFORE_SEC] before (a build-up show
 * that runs into the game, as Sky's "Live PL" from 90 minutes before), is not a replay or
 * highlights, and names the teams (see [TeamNameForms]).
 *
 * What is built from a channel list (folded names, a word index) is kept for the whole process
 * while the same list object is passed ([SportsChannels] keeps one list per profile and catalogue).
 */
class GameChannelMatcher(
    private val guide: GuideLookup,
) {
    companion object {
        const val BEFORE_SEC = 60 * 60L
        const val AFTER_SEC = 30 * 60L
        const val ON_AIR_BEFORE_SEC = 3 * 60 * 60L

        /** Whether [p] starts near [startSec], or runs over it after starting not long before. */
        internal fun isAround(
            p: GuideProgramme,
            startSec: Long,
        ): Boolean =
            p.startSec in (startSec - BEFORE_SEC)..(startSec + AFTER_SEC) ||
                (p.startSec in (startSec - ON_AIR_BEFORE_SEC)..startSec && p.endSec > startSec)

        // The last channel list prepared, for every matcher of the process.
        @Volatile private var prepared: Prepared? = null

        private fun prepare(channels: List<LiveChannel>): Prepared =
            prepared?.takeIf { it.channels === channels } ?: synchronized(this) {
                prepared?.takeIf { it.channels === channels } ?: Prepared(channels).also { prepared = it }
            }

        /** Drops what was built from the last channel list; see [SportsChannels.drop]. */
        internal fun drop() {
            prepared = null
        }

        /** The same channel name in two categories or sources: one key. */
        private fun nameKey(name: String): String = name.trim().replace(SPACES, " ").lowercase()

        private val SPACES = Regex("\\s+")
    }

    /**
     * What is built from one channel list: the visible channels, their names as broadcaster words
     * and as folded text, and indexes from a word to the channels that have it.
     */
    private class Prepared(
        val channels: List<LiveChannel>,
    ) {
        val visible: List<LiveChannel> = channels.filter { !it.hidden && !it.name.trimStart().startsWith("#") }
        val parsed: Array<BroadcasterChannels.ParsedChannel> = Array(visible.size) { BroadcasterChannels.parse(visible[it].name) }
        val padded: Array<String> = Array(visible.size) { SportsText.padded(visible[it].name) }

        // Broadcaster words: the first word of a channel, and all its words joined.
        val byFirstWord: Map<String, IntArray> = index { i -> listOfNotNull(parsed[i].words.firstOrNull()) }
        val byJoined: Map<String, IntArray> = index { i -> listOf(parsed[i].joined) }

        // Every word of a channel's folded name.
        val byWord: Map<String, IntArray> = index { i -> padded[i].split(' ').filter { it.isNotEmpty() }.distinct() }

        val resolver by lazy { GuideChannelResolver(channels) }

        private fun index(words: (Int) -> List<String>): Map<String, IntArray> {
            val lists = HashMap<String, IntList>()
            for (i in visible.indices) for (w in words(i)) lists.getOrPut(w) { IntList() }.add(i)
            return lists.mapValues { it.value.toArray() }
        }

        /** The channels with any of [words], in list order. */
        fun withAnyWord(words: Collection<String>): IntArray {
            val hits = words.mapNotNull { byWord[it] }
            if (hits.size == 1) return hits[0]
            return hits
                .flatMap { it.asIterable() }
                .toSortedSet()
                .toIntArray()
        }
    }

    /** A growing list of ints, for the indexes. */
    private class IntList {
        private var items = IntArray(4)
        private var size = 0

        fun add(value: Int) {
            if (size == items.size) items = items.copyOf(size * 2)
            items[size++] = value
        }

        fun toArray(): IntArray = items.copyOf(size)
    }

    /** The ways to watch [game] on [channels] (the profile's live channels), best first. Pass the same list for each game. */
    suspend fun match(
        game: SportsGame,
        channels: List<LiveChannel>,
    ): List<GameChannel> {
        val fromSec = game.startEpochSec - ON_AIR_BEFORE_SEC
        val toSec = game.startEpochSec + AFTER_SEC
        val text = GameText(game)

        val prepared = prepare(channels)
        val visible = prepared.visible
        val broadcasters = game.broadcasters.distinctBy { it.name.lowercase() to it.country }
        val channelsOf: List<Pair<Broadcaster, List<Int>>> =
            broadcasters.map { b ->
                val tokens = BroadcasterChannels.broadcasterTokens(b.name)
                val candidates =
                    listOfNotNull(prepared.byFirstWord[tokens.firstOrNull()], prepared.byJoined[tokens.joinToString("")])
                        .flatMap { it.asIterable() }
                        .distinct()
                        .sorted()
                b to candidates.filter { BroadcasterChannels.matches(b, tokens, prepared.parsed[it]) }
            }
        val broadcasterOf = HashMap<LiveChannel, Broadcaster>()
        channelsOf.forEach { (b, list) -> list.forEach { broadcasterOf.putIfAbsent(visible[it], b) } }

        val result = ArrayList<GameChannel>()
        val used = HashSet<Pair<Long, Int>>()
        val usedNames = HashSet<String>()

        fun add(
            channel: LiveChannel,
            programme: GuideProgramme?,
            how: GameChannelHow,
        ) {
            if (used.add(channel.providerId to channel.streamId) && usedNames.add(nameKey(channel.name))) {
                result += GameChannel(channel, programme, broadcasterOf[channel], how)
            }
        }

        // 1. The game in the guide of a broadcaster's channel.
        val broadcasterChannels = broadcasterOf.keys
        val epgIds = broadcasterChannels.mapNotNull { it.epgChannelId?.takeIf(String::isNotBlank) }.distinct()
        if (epgIds.isNotEmpty()) {
            val programmes =
                guide
                    .programmesOnChannels(epgIds, fromSec, toSec)
                    .filter { isAround(it, game.startEpochSec) && text.isGameOnBroadcaster(it) }
            broadcasterChannels
                .mapNotNull { channel ->
                    programmes
                        .filter { it.providerId == channel.providerId && it.channelId.equals(channel.epgChannelId, ignoreCase = true) }
                        .maxByOrNull { text.score(it) }
                        ?.let { channel to it }
                }.sortedWith(compareByDescending<Pair<LiveChannel, GuideProgramme>> { text.score(it.second) }.thenBy { it.first.name })
                .forEach { (channel, programme) -> add(channel, programme, GameChannelHow.GUIDE_ON_BROADCASTER) }
        }

        // 2. The game anywhere in the guide, by the team names.
        val terms = text.searchTerms()
        if (terms.isNotEmpty()) {
            val found =
                guide
                    .searchTitles(terms, fromSec, toSec)
                    .filter { isAround(it, game.startEpochSec) && text.isGameAnywhere(it) }
                    // Ties in a fixed order, so that a second read gives the same list.
                    .sortedWith(
                        compareByDescending<GuideProgramme> { text.score(it) }
                            .thenBy { it.channelName }
                            .thenBy { it.providerId }
                            .thenBy { it.channelId },
                    )
            for (programme in found) {
                val channel = prepared.resolver.resolve(programme) ?: continue
                add(channel, programme, GameChannelHow.GUIDE)
            }
        }

        // 3. The channels whose names name both teams: first the channels with a word of each team (the index), then the full check.
        val awayWords = text.awayFirstWords
        if (awayWords != null) {
            val withAway = prepared.withAnyWord(awayWords).toHashSet()
            prepared
                .withAnyWord(text.homeFirstWords)
                .filter { it in withAway }
                .map { visible[it] to prepared.padded[it] }
                .filter { (channel, padded) ->
                    text.namesGame(channel.name, padded) && ChannelNameDates.agrees(channel.name, game.startEpochSec, text.nameWords)
                }.sortedBy { it.first.name }
                .forEach { (channel, _) -> add(channel, null, GameChannelHow.CHANNEL_NAME) }
        }

        // 4. The broadcasters' channels without a programme.
        for ((b, list) in channelsOf) {
            val listed = list.map { visible[it] }
            BroadcasterChannels.likelyChannels(b, listed, listed.indices.associate { listed[it] to prepared.parsed[list[it]] }).forEach {
                add(it, null, GameChannelHow.BROADCASTER_CHANNEL)
            }
        }
        return result
    }
}

/**
 * Matches a guide channel to a live stream of the programme's own source as the guide search does
 * ([EpgChannelMatcher]: guide id, then name). A hidden stream matches but is not returned.
 */
private class GuideChannelResolver(
    channels: List<LiveChannel>,
) {
    private val byKey = channels.associateBy { it.providerId to it.streamId }
    private val matchers =
        channels.groupBy { it.providerId }.mapValues { (providerId, list) ->
            EpgChannelMatcher(list.map { it.toStreamEntity() }, providerId)
        }

    fun resolve(programme: GuideProgramme): LiveChannel? {
        val matched = matchers[programme.providerId]?.match(programme.channelId, programme.channelName) ?: return null
        return byKey[matched.providerId to matched.streamId]?.takeUnless { it.hidden }
    }

    private fun LiveChannel.toStreamEntity() =
        XtreamStreamEntity(
            streamId = streamId,
            providerId = providerId,
            type = XtreamStreamEntity.TYPE_LIVE,
            num = 0,
            name = name,
            streamType = "live",
            epgChannelId = epgChannelId,
            categoryId = categoryId,
        )
}
