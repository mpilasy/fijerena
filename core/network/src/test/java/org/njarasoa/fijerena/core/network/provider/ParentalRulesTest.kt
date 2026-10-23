package org.njarasoa.fijerena.core.network.provider

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A child profile's rules with the app's starting lists, on names from three real catalogues
 * (docs/plans/20261020_parental-controls-plan.md).
 */
class ParentalRulesTest {
    private val rules = ParentalRules()

    @Test
    fun `kids' and family categories in many languages show`() {
        listOf(
            "UK| KIDS ᴴᴰ/ᴿᴬᵂ",
            "|EN| CHILDREN/FAMILY",
            "EN - FAMILY",
            "FR - ENFANTS",
            "|FR| JEUNESSE 4K/3D",
            "DE - KINDER FILME",
            "NL - KINDEREN",
            "|ES| NIÑOS/ANIMACION",
            "ES| INFANTIL",
            "IT| BAMBINI",
            "|PT| CRIANÇAS",
            "PL - DZIECI",
            "|PL| BAJKI",
            "SVENSKA BARN",
            "VOD - BARNEFILMER [NO]",
            "VOD - BØRNEFILM [DK]",
            "VOD - LASTEN ELOKUVIA [FI]",
            "TR| ÇOCUKLAR 4K",
            "|TR| COCUK",
            "RO| CANALE PENTRU COPII",
            "CZ| DĚTI",
            "EXYU| DECIJI KANALI",
            "SRS - CRTANE SERIJE [EXYU]",
            "AL - PËR FËMIJË",
            "GR - ΠΑΙΔΙΚΑ",
            "GR| ΠΑΙΔΙΚΆ/KIDS",
            "|AR| أفلام اطفال",
            "مسلسلات أطفال",
            "|EN| CLASSIC CARTOONS",
            "|FR| DISNEY",
            "DISNEY+ KIDS MOVIES",
            "|EN| PIXAR MOVIES",
            "DREAMWORKS ANIMATION",
            "DE - STUDIO GHIBLI",
            "NICKELODEON",
            "VIDEOLAND KID (NL)",
        ).forEach { assertTrue(it, rules.showsCategory(it)) }
    }

    @Test
    fun `other categories do not, even when they look close`() {
        listOf(
            "DISNEY+ SERIES",
            "VOD - DISNEY+ [FR]",
            "|NL| DISNEY +",
            "|ES| DRAMA/ROMANCE/FAMILIA",
            "FR - ANIMATION",
            "|FR| DISNEY+  ANIME",
            "US| PBS NETWORK",
            "UK| BBC IPLAYER",
            "KIDNAPPING THRILLERS",
            "SPORTS",
        ).forEach { assertFalse(it, rules.showsCategory(it)) }
    }

    @Test
    fun `Animation shows for films only, Anime and Manga never`() {
        listOf(
            "FR - ANIMATION ⁴ᴷ ᴴᴰᴿ ᴰᴼᴸᴮʸ ᴬᵁᴰᴵᴼ",
            "IT - ANIMAZIONE",
            "ES - PELÍCULAS ANIMACION",
            "PT/BR - ANIMAÇÃO",
            "|IN| HINDI ANIMATION",
        ).forEach { assertTrue(it, rules.showsCategory(it, films = true)) }
        listOf("ESPAÑA ANIMACIÓN", "|FR| ANIMATION", "CHINA ANIMATION")
            .forEach { assertFalse(it, rules.showsCategory(it)) }
        listOf("|EN| ANIME", "DE - ANIME FILME", "|FR| MANGA", "NETFLIX ANIME")
            .forEach { assertFalse(it, rules.showsCategory(it, films = true)) }
    }

    @Test
    fun `adult categories never show, also when they name kids`() {
        listOf(
            "ADULT: ALBA XXX 2 (H)",
            "FOR ADULTS",
            "XXX | VOD",
            "Adultos",
            "Films pour adultes",
            "18+ Movies",
            "VOD +18",
            "Porno HD",
            "EROTIC",
            "Erotik TV",
            "NSFW",
            "Playboy",
            "ADULT KIDS",
        ).forEach {
            assertTrue(it, rules.isAdultCategory(it))
            assertFalse(it, rules.showsCategory(it, films = true))
        }
        listOf("UK| BBC IPLAYER", "Sussex", "ESSEX RADIO", "Kids 4-8", "MOVIES 2018", "Top 180 films", "Sports 118")
            .forEach { assertFalse(it, rules.isAdultCategory(it)) }
    }

    @Test
    fun `hidden titles are adult labels, adult brands, adult animation and horror`() {
        listOf(
            "The Intern - A Summer of Lust (Adult content)",
            "XXX Hotel",
            "Erotic Tales",
            "Movie 18+",
            "Adults only night",
            "AL - South Park (Not Suitable for Children) (2023)",
            "4K-FR - Sausage Party",
            "AL - Winnie-the-Pooh: Blood and Honey 2 (2024)",
            "EN - Rick and Morty",
            "UK - FAMILY GUY",
            "US: ADULT SWIM HD",
            "EN - Love, Death & Robots",
            "Beavis and Butt-Head Do America",
            "The Bob’s Burgers Movie (2022)",
            "CG: BRAZZERS TV",
            "CG: PRIVATE TV",
            "PH - RATED R",
        ).forEach { assertTrue(it, rules.hidesTitle(it)) }
        listOf(
            "Adults in the Room",
            "Adulthood",
            "Sex Education",
            "Top Gun (1986)",
            "Canal+ 1",
            "Toy Story (1995)",
            "Saving Private Ryan",
            "The Naked Gun",
            "Toy Story of Terror!",
            "FR - LES SIMPSONS 1",
            "Family Switch",
        ).forEach { assertFalse(it, rules.hidesTitle(it)) }
    }

    @Test
    fun `exceptions let titles through, accents ignored, but never a hidden title`() {
        listOf("EN - Naruto Shippūden (2007)", "FR - One Piece Film: Red", "Pokémon: Detective Pikachu", "DE - Yu-Gi-Oh! GX")
            .forEach { assertTrue(it, rules.allowsTitle(it)) }
        listOf("Narutomaki", "Toy Story", "Pokemon XXX")
            .forEach { assertFalse(it, rules.allowsTitle(it)) }
    }

    @Test
    fun `an entry is whole words, any case, with an open end after a star`() {
        val matcher = EntryMatcher(listOf("barn*", "rated r", "  ", "*"))
        assertTrue(matcher.matches("NORDIC BARNEFILM"))
        assertTrue(matcher.matches("PH - Rated  R"))
        assertFalse(matcher.matches("Rated Run"))
        assertFalse(matcher.matches("SUPERBARN"))
        assertFalse(EntryMatcher(emptyList()).matches("anything"))
    }

    @Test
    fun `an admin's own lists replace the app's`() {
        val own = ParentalRules(kidsCategories = listOf("dessins animés"), exceptions = listOf("toy story"))
        assertTrue(own.showsCategory("FR - DESSINS ANIMES"))
        assertFalse(own.showsCategory("UK| KIDS"))
        assertTrue(own.allowsTitle("Toy Story 4"))
    }
}
