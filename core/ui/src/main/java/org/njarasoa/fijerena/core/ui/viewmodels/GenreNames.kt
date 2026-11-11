package org.njarasoa.fijerena.core.ui.viewmodels

import java.text.Normalizer
import java.util.Locale

/**
 * The same genre in several languages, so Search's filter has one choice for it: a title TMDB
 * filled in has its genres in the app language ("Comédie"), another keeps its source's ("Comedy").
 * Each genre's key is its English name in lower case; its spellings are by language (ISO 639), and
 * the filter shows the spelling of the app language when a result has it.
 */
internal object GenreNames {
    // First: the lookup below is built with them.
    private val marks = Regex("\\p{Mn}+")
    private val spaces = Regex("[\\s-]+")

    private val GENRES: Map<String, Map<String, String>> =
        mapOf(
            "action" to names("Action", "Action", "Action", "Acción", "Azione", "Ação"),
            "adventure" to names("Adventure", "Aventure", "Abenteuer", "Aventura", "Avventura", "Aventura"),
            "animation" to names("Animation", "Animation", "Animation", "Animación", "Animazione", "Animação"),
            "comedy" to names("Comedy", "Comédie", "Komödie", "Comedia", "Commedia", "Comédia"),
            "crime" to names("Crime", "Crime", "Krimi", "Crimen", "Crime", "Crime"),
            "documentary" to names("Documentary", "Documentaire", "Dokumentarfilm", "Documental", "Documentario", "Documentário"),
            "drama" to names("Drama", "Drame", "Drama", "Drama", "Dramma", "Drama"),
            "family" to names("Family", "Familial", "Familie", "Familia", "Famiglia", "Família"),
            "fantasy" to names("Fantasy", "Fantastique", "Fantasy", "Fantasía", "Fantasy", "Fantasia"),
            "history" to names("History", "Histoire", "Historie", "Historia", "Storia", "História"),
            "horror" to names("Horror", "Horreur", "Horror", "Terror", "Horror", "Terror"),
            "music" to names("Music", "Musique", "Musik", "Música", "Musica", "Música"),
            "mystery" to names("Mystery", "Mystère", "Mystery", "Misterio", "Mistero", "Mistério"),
            "romance" to names("Romance", "Romance", "Liebesfilm", "Romance", "Romance", "Romance"),
            "science fiction" to
                names("Science Fiction", "Science-Fiction", "Science Fiction", "Ciencia ficción", "Fantascienza", "Ficção científica"),
            "thriller" to names("Thriller", "Thriller", "Thriller", "Suspense", "Thriller", "Thriller"),
            "war" to names("War", "Guerre", "Kriegsfilm", "Bélica", "Guerra", "Guerra"),
            "western" to names("Western", "Western", "Western", "Western", "Western", "Western"),
            "tv movie" to names("TV Movie", "Téléfilm", "TV-Film", "Película de TV", "Film TV", "Cinema TV"),
        )

    /** Other spellings sources use for a genre. */
    private val EXTRA: Map<String, String> =
        mapOf(
            "sci-fi" to "science fiction",
            "scifi" to "science fiction",
            "sf" to "science fiction",
            "documentaries" to "documentary",
            "comedie" to "comedy",
        )

    private val byName: Map<String, String> =
        buildMap {
            GENRES.forEach { (key, spellings) -> spellings.values.forEach { put(normal(it), key) } }
            EXTRA.forEach { (name, key) -> put(normal(name), key) }
        }

    /** [genre]'s key: one key for the genre in any language, case or accent; else the text in lower case. */
    fun key(genre: String): String = byName[normal(genre)] ?: genre.lowercase(Locale.ROOT)

    /** True when [spelling] is how [language] writes the genre of [key]. */
    fun isSpellingOf(
        key: String,
        spelling: String,
        language: String,
    ): Boolean = GENRES[key]?.get(language)?.let { normal(it) == normal(spelling) } == true

    private fun names(
        en: String,
        fr: String,
        de: String,
        es: String,
        it: String,
        pt: String,
    ) = mapOf("en" to en, "fr" to fr, "de" to de, "es" to es, "it" to it, "pt" to pt)

    private fun normal(text: String): String =
        Normalizer
            .normalize(text.trim().lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(marks, "")
            .replace(spaces, " ")
}
