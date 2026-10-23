package org.njarasoa.fijerena.core.network.provider

import java.text.Normalizer
import java.util.Locale

/**
 * The five word lists of Settings › Parental controls (docs/plans/20261020_parental-controls-plan.md).
 * They decide what a child profile sees, on every source. They are the same for every child profile,
 * synced to every device, and edited by the admin. [defaults] fills a list once; after that only the
 * admin changes it.
 */
enum class ParentalList(
    val key: String,
    val defaults: List<String>,
) {
    /** A category shows to a child when its name has one of these. */
    KIDS_CATEGORIES(
        "parental_kids_categories",
        listOf(
            "kid",
            "kids",
            "children",
            "family",
            "famille",
            "familijne",
            "enfant",
            "enfants",
            "jeunesse",
            "kinder",
            "kinderen",
            "ninos",
            "infantil",
            "infantiles",
            "bambini",
            "figli",
            "criancas",
            "dzieci",
            "bajki",
            "barn",
            "barne*",
            "børn*",
            "lasten",
            "cocuk*",
            "copii",
            "deti",
            "deciji",
            "decije",
            "crtani",
            "crtane",
            "femije",
            "παιδικ*",
            "παιδια",
            "اطفال",
            "cartoon",
            "cartoons",
            "disney",
            "pixar",
            "dreamworks",
            "ghibli",
            "nickelodeon",
        ),
    ),

    /** Also shows, for a film category only: these categories hold mostly children's films. */
    KIDS_FILM_CATEGORIES(
        "parental_kids_film_categories",
        listOf("animation", "animations", "animacion", "animacao", "animazione", "animatie", "animationsfilm*"),
    ),

    /** A category with one of these never shows to a child. */
    ADULT_CATEGORIES(
        "parental_adult_categories",
        listOf("xxx*", "adult*", "porn*", "erotic*", "erotik*", "nsfw", "playboy", "sex", "sexy", "18+", "+18"),
    ),

    /**
     * A title with one of these never shows to a child, also in a kids' category: adult labels,
     * adult channel brands, and the adult animation and horror that servers put in kids' categories.
     */
    HIDDEN_TITLES(
        "parental_hidden_titles",
        listOf(
            "xxx*",
            "porn*",
            "erotic*",
            "erotik*",
            "nsfw",
            "playboy",
            "adult content",
            "adults only",
            "not suitable for children",
            "rated r",
            "rated-r",
            "18+",
            "+18",
            "milf",
            "fetish*",
            "hentai",
            "threesome",
            "brazzers",
            "dorcel",
            "hustler",
            "penthouse",
            "vivid",
            "redlight",
            "babestation",
            "private tv",
            "south park",
            "sausage party",
            "big mouth",
            "bojack",
            "rick and morty",
            "family guy",
            "american dad",
            "hazbin hotel",
            "helluva boss",
            "invincible",
            "castlevania",
            "harley quinn",
            "robot chicken",
            "love, death & robots",
            "love death & robots",
            "love, death and robots",
            "love death and robots",
            "beavis and butt-head",
            "beavis and butthead",
            "archer",
            "solar opposites",
            "krapopolis",
            "paradise pd",
            "smiling friends",
            "devilman",
            "chainsaw man",
            "hellsing",
            "blood and honey",
            "adult swim",
            "aqua teen",
            "futurama",
            "bob's burgers",
            "daria",
            "one punch man",
            "no guns life",
            "attack on titan",
            "jujutsu kaisen",
            "berserk",
            "killing joke",
        ),
    ),

    /** A film or series with one of these shows to a child, also from a category the lists hide. */
    EXCEPTIONS(
        "parental_exceptions",
        listOf(
            "pokemon",
            "doraemon",
            "one piece",
            "naruto",
            "boruto",
            "dragon ball",
            "detective conan",
            "sailor moon",
            "yu-gi-oh",
            "yugioh",
        ),
    ),
}

/**
 * A child profile's rules from the [ParentalList]s. Adult categories and hidden titles always win;
 * then a category shows when it is a kids' one (or, for films, a kids' film one); then a film or
 * series shows when it matches an exception, also in a hidden category.
 */
data class ParentalRules(
    val kidsCategories: List<String> = ParentalList.KIDS_CATEGORIES.defaults,
    val kidsFilmCategories: List<String> = ParentalList.KIDS_FILM_CATEGORIES.defaults,
    val adultCategories: List<String> = ParentalList.ADULT_CATEGORIES.defaults,
    val hiddenTitles: List<String> = ParentalList.HIDDEN_TITLES.defaults,
    val exceptions: List<String> = ParentalList.EXCEPTIONS.defaults,
) {
    private val kids by lazy { EntryMatcher(kidsCategories) }
    private val kidsFilms by lazy { EntryMatcher(kidsFilmCategories) }
    private val adult by lazy { EntryMatcher(adultCategories) }
    private val hidden by lazy { EntryMatcher(hiddenTitles) }
    private val allowed by lazy { EntryMatcher(exceptions) }

    /** A category a child may see. [films]: it is a film category. */
    fun showsCategory(
        categoryName: String,
        films: Boolean = false,
    ): Boolean = !adult.matches(categoryName) && (kids.matches(categoryName) || (films && kidsFilms.matches(categoryName)))

    /** A category no child may see, whatever the exceptions. */
    fun isAdultCategory(categoryName: String): Boolean = adult.matches(categoryName)

    /** A title no child may see, in any category. */
    fun hidesTitle(title: String): Boolean = hidden.matches(title)

    /** A film or series a child may see also in a hidden category (not an adult one: see [isAdultCategory]). */
    fun allowsTitle(title: String): Boolean = !hidden.matches(title) && allowed.matches(title)
}

/**
 * Matches text against a list's entries: whole words, any case, accents ignored. A `*` at the end of
 * an entry matches any ending. `+` is part of a word, so "disney" does not match "Disney+" and "18+"
 * is one word; "Disney +" counts as "Disney+".
 */
class EntryMatcher(
    entries: List<String>,
) {
    private val regex: Regex? =
        entries
            .map { prepare(it.trim()) }
            .filter { it.isNotEmpty() && it != "*" }
            .map(::entryPattern)
            .takeIf { it.isNotEmpty() }
            ?.let { Regex("(?<!$WORD)(?:${it.joinToString("|")})(?!$WORD)") }

    fun matches(text: String): Boolean = regex?.containsMatchIn(prepare(text)) == true

    companion object {
        private const val WORD = "[\\p{L}\\p{N}_+]"
        private val marks = Regex("\\p{Mn}+")
        private val spacedPlus = Regex("\\s+\\+(?![\\p{L}\\p{N}])")
        private val spaces = Regex("\\s+")

        /** Lower case, accents off, curly apostrophes straight, "Disney +" as "Disney+". */
        fun prepare(text: String): String =
            Normalizer
                .normalize(text.lowercase(Locale.ROOT).replace('’', '\''), Normalizer.Form.NFD)
                .replace(marks, "")
                .replace(spacedPlus, "+")

        private fun entryPattern(entry: String): String {
            val open = entry.endsWith("*")
            val words =
                entry
                    .removeSuffix("*")
                    .trim()
                    .split(spaces)
                    .joinToString("\\s+") { Regex.escape(it) }
            return if (open) "$words$WORD*" else words
        }
    }
}
