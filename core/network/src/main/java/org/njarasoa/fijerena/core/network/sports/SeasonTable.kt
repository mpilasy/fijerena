package org.njarasoa.fijerena.core.network.sports

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

/**
 * The season table: the broadcasters of each competition per country and season, from the asset
 * [ASSET] (see the sports teams plan → The season table). It gives TV for the countries that no
 * live source covers. The asset is read once, off the main thread, on the first call.
 *
 * Update the asset each season and run `scripts/check-sports-table.sh`.
 */
class SeasonTable(
    private val readJson: () -> String,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    @Serializable
    internal data class TableFile(
        val version: Int,
        val updated: String,
        val regions: Map<String, List<String>> = emptyMap(),
        val competitions: List<Competition>,
        val rights: List<Rights>,
    )

    /**
     * A competition. [key] is the table's own key; [ids] is the competition's id in each source, by
     * [SportsSource.id]; [names] are the other names sources and guides give it.
     */
    @Serializable
    internal data class Competition(
        val key: String,
        val name: String,
        val sport: String,
        val ids: Map<String, String> = emptyMap(),
        val names: List<String> = emptyList(),
        val sources: List<String> = emptyList(),
    )

    /**
     * The [broadcasters] of [competition] in [countries] from [from] to [to] (ISO dates, both
     * included). A country is an ISO 3166-1 alpha-2 code, a key of [TableFile.regions], or [WORLD]
     * for a service that shows the games in every country.
     */
    @Serializable
    internal data class Rights(
        val competition: String,
        val season: String,
        val from: String,
        val to: String,
        val countries: List<String>,
        val broadcasters: List<String>,
    )

    private class Period(
        val from: LocalDate,
        val to: LocalDate,
        val countries: List<String?>,
        val broadcasters: List<String>,
    )

    private class Loaded(
        val byId: Map<Pair<String, String>, String>,
        val byName: Map<String, String>,
        val periods: Map<String, List<Period>>,
    )

    private val lock = Mutex()
    private var loaded: Loaded? = null

    /**
     * The broadcasters of [competition] in every country whose rights cover the UTC date of
     * [startEpochSec], in the table's order; a worldwide service has a null country. Empty when
     * the table does not know the competition.
     */
    suspend fun broadcasters(
        competition: SportsLeague,
        startEpochSec: Long,
    ): List<Broadcaster> {
        val table = table()
        val key = table.byId[competition.source.id to idKey(competition.id)] ?: table.byName[normalize(competition.name)]
        val date = Instant.ofEpochSecond(startEpochSec).atZone(ZoneOffset.UTC).toLocalDate()
        return table.periods[key]
            .orEmpty()
            .filter { !date.isBefore(it.from) && !date.isAfter(it.to) }
            .flatMap { period ->
                period.countries.flatMap { country ->
                    period.broadcasters.map { Broadcaster(it, country, from = BroadcasterOrigin.SEASON_TABLE) }
                }
            }.distinctBy { it.country to it.name }
    }

    /** The broadcasters of [game]'s competition on the game's date. */
    suspend fun broadcasters(game: SportsGame): List<Broadcaster> = broadcasters(game.competition, game.startEpochSec)

    private suspend fun table(): Loaded =
        lock.withLock {
            loaded ?: withContext(ioDispatcher) { load(readJson()) }.also { loaded = it }
        }

    private fun load(text: String): Loaded {
        val file = json.decodeFromString(TableFile.serializer(), text)
        val byId = mutableMapOf<Pair<String, String>, String>()
        val byName = mutableMapOf<String, String>()
        file.competitions.forEach { competition ->
            competition.ids.forEach { (source, id) -> byId[source to idKey(id)] = competition.key }
            (listOf(competition.name) + competition.names).forEach { byName.putIfAbsent(normalize(it), competition.key) }
        }
        val periods =
            file.rights.groupBy({ it.competition }) { rights ->
                val countries =
                    rights.countries.flatMap { country ->
                        when (country) {
                            WORLD -> listOf(null)
                            in file.regions -> file.regions.getValue(country)
                            else -> listOf(country.uppercase(Locale.ROOT))
                        }
                    }
                Period(LocalDate.parse(rights.from), LocalDate.parse(rights.to), countries.distinct(), rights.broadcasters)
            }
        return Loaded(byId, byName, periods)
    }

    companion object {
        const val ASSET = "sports_broadcasters.json"

        /** A [Rights] country for a service that shows the games everywhere: its broadcasters have no country. */
        const val WORLD = "WORLD"

        private val json = Json { ignoreUnknownKeys = true }
        private val marks = Regex("\\p{Mn}+")
        private val notLetters = Regex("[^a-z0-9]+")

        /** The table in the app's assets. */
        fun fromAssets(context: Context): SeasonTable {
            val assets = context.applicationContext.assets
            return SeasonTable({ assets.open(ASSET).bufferedReader().use { it.readText() } })
        }

        /** A competition name without case, accents or punctuation: "LaLiga EA Sports" → "laliga ea sports". */
        internal fun normalize(name: String): String =
            Normalizer
                .normalize(name, Normalizer.Form.NFD)
                .replace(marks, "")
                .lowercase(Locale.ROOT)
                .replace(notLetters, " ")
                .trim()

        // A source id compared without case and without its sport path, so ESPN's "soccer/eng.1"
        // and "eng.1" find the same competition.
        private fun idKey(id: String): String = id.substringAfterLast('/').lowercase(Locale.ROOT)
    }
}
