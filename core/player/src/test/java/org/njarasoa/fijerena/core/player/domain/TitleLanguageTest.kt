package org.njarasoa.fijerena.core.player.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TitleLanguageTest {
    @Test
    fun `language prefix becomes badge`() {
        assertEquals(ParsedTitle("Breaking Bad", "EN"), parseDisplayTitle("EN - Breaking Bad"))
        assertEquals(ParsedTitle("Kantipur", "NP"), parseDisplayTitle("NP:Kantipur"))
        assertEquals(ParsedTitle("Bruins vs Leafs", "NHL"), parseDisplayTitle("NHL: Bruins vs Leafs"))
    }

    @Test
    fun `D+ and A+ prefixes become uppercase badges in either case`() {
        assertEquals(ParsedTitle("Loki", "D+"), parseDisplayTitle("D+ - Loki"))
        assertEquals(ParsedTitle("Loki", "D+"), parseDisplayTitle("d+: Loki"))
        assertEquals(ParsedTitle("Severance", "A+"), parseDisplayTitle("A+ - Severance"))
        assertEquals(ParsedTitle("Severance", "A+"), parseDisplayTitle("a+ - Severance"))
    }

    @Test
    fun `D+ and A+ suffixes become badges`() {
        assertEquals(ParsedTitle("Loki", "D+"), parseDisplayTitle("Loki (D+)"))
        assertEquals(ParsedTitle("Severance", "A+"), parseDisplayTitle("Severance (a+)"))
    }

    @Test
    fun `ordinary title casing is not badged`() {
        assertEquals(ParsedTitle("A-Team", null), parseDisplayTitle("A-Team"))
    }

    @Test
    fun `4K catalogue tags split into the code and the formats`() {
        assertEquals(
            ParsedTitle("The Perfect Lie (2026)", "NF", listOf("4K"), year = 2026),
            parseDisplayTitle("4K-NF - The Perfect Lie (2026) (NL)"),
        )
        assertEquals(ParsedTitle("Silo (2023)", "A+", listOf("4K"), year = 2023), parseDisplayTitle("4K-A+ - Silo (2023) (US)"))
        assertEquals(ParsedTitle("Loki", "D+", listOf("4K")), parseDisplayTitle("4K-D+ - Loki"))
        assertEquals(ParsedTitle("Neagley (2026)", "AMZ", listOf("4K"), year = 2026), parseDisplayTitle("4K-AMZ - Neagley (2026) (US)"))
        assertEquals(ParsedTitle("Dune", "FR", listOf("4K", "HDR")), parseDisplayTitle("4K-FR-HDR - Dune"))
        assertEquals(ParsedTitle("Dune", "OSN+", listOf("4K")), parseDisplayTitle("4K-OSN+ - Dune"))
        assertEquals(ParsedTitle("Dune", "FR", listOf("4K")), parseDisplayTitle("4k-FR - Dune"))
        assertEquals(ParsedTitle("Dune", "DE", listOf("4K")), parseDisplayTitle("4K-DE  - Dune"))
        assertEquals(
            ParsedTitle("Skyscraper (2018)", "DE", listOf("4K", "DV"), year = 2018),
            parseDisplayTitle("4K-DE-DV - Skyscraper (2018)"),
        )
        assertEquals(
            ParsedTitle("The Last of Us (2023)", "DE-BLURAY", listOf("4K", "DV"), year = 2023),
            parseDisplayTitle("DE-4K-BLURAY-DV - The Last of Us (2023) (US)"),
        )
        assertEquals(
            ParsedTitle("4 Blocks Zero (2026)", "DE", listOf("4K", "DV"), year = 2026),
            parseDisplayTitle("DE- [4K-DV-HDR] - 4 Blocks Zero (2026) (DE)"),
        )
        assertEquals(
            ParsedTitle("Heart Of The Beast (2026)", "CAM", listOf("SD"), year = 2026),
            parseDisplayTitle("SD/CAM - Heart Of The Beast (2026)"),
        )
    }

    @Test
    fun `a title starting with 4K- but no tag separator is not badged`() {
        assertEquals(ParsedTitle("4K-Ultra Nature", null), parseDisplayTitle("4K-Ultra Nature"))
    }

    @Test
    fun `a leading format alone is a tag`() {
        assertEquals(ParsedTitle("BEIN SPORTS", null, listOf("4K")), parseDisplayTitle("4K: BEIN SPORTS"))
        assertEquals(ParsedTitle("TNT", null, listOf("4K")), parseDisplayTitle("4k:TNT"))
        assertEquals(ParsedTitle("COCINA", null, listOf("4K")), parseDisplayTitle("4K| COCINA UHD"))
        assertEquals(ParsedTitle("TF1", null, listOf("4K", "HDR")), parseDisplayTitle("|4K| TF1 HDR/UHD/4K"))
        assertEquals(
            ParsedTitle("A Knight of the Seven Kingdoms (2026)", "US", listOf("4K"), year = 2026),
            parseDisplayTitle("4K - A Knight of the Seven Kingdoms (2026) (US)"),
        )
    }

    @Test
    fun `a pipe ends a live channel's code`() {
        assertEquals(ParsedTitle("ZDF", "DE", listOf("HD")), parseDisplayTitle("DE| ZDF HD"))
        assertEquals(ParsedTitle("DAS ERSTE", "DE"), parseDisplayTitle("DE| DAS ERSTE"))
    }

    @Test
    fun `formats at the end become tags, one per kind`() {
        assertEquals(ParsedTitle("RTL", "DE", listOf("4K")), parseDisplayTitle("DE| RTL 4K FHD"))
        assertEquals(ParsedTitle("NPO1", "NL", listOf("8K")), parseDisplayTitle("NL| NPO1 UHD 8K"))
        assertEquals(ParsedTitle("LA 1", "ES", listOf("4K")), parseDisplayTitle("ES| LA 1 4K/UHD"))
        assertEquals(ParsedTitle("BBC NEWS", "UK", listOf("FHD", "HEVC")), parseDisplayTitle("UK| BBC NEWS HEVC FHD"))
        assertEquals(ParsedTitle("WATCHBOX MAX", "AR", listOf("HEVC")), parseDisplayTitle("AR| WATCHBOX MAX H265"))
        assertEquals(ParsedTitle("NPO 1", "NL", listOf("LQ")), parseDisplayTitle("NL| NPO 1 LQ"))
        assertEquals(ParsedTitle("Titanic (1997)", "NL", listOf("HEVC"), year = 1997), parseDisplayTitle("NL - Titanic (1997) HEVC"))
        assertEquals(ParsedTitle("Scream 7 (2026)", "FR", listOf("LQ"), year = 2026), parseDisplayTitle("FR - Scream 7 (2026) LQ"))
    }

    @Test
    fun `formats before the year or in brackets become tags`() {
        assertEquals(ParsedTitle("Joker (2019)", "DE", listOf("4K"), year = 2019), parseDisplayTitle("DE - Joker 4K (2019) (4K)"))
        assertEquals(ParsedTitle("355 (2022)", "FR", listOf("4K"), year = 2022), parseDisplayTitle("FR - 355 4K (2022)"))
        assertEquals(ParsedTitle("Zeta (2026)", "ES", listOf("4K"), year = 2026), parseDisplayTitle("ES - Zeta [UHD] (2026)"))
        assertEquals(ParsedTitle("Un hijo (2026)", "ES", listOf("LQ"), year = 2026), parseDisplayTitle("ES - Un hijo (LQ) (2026)"))
        assertEquals(ParsedTitle("Suits (2011)", "EN", listOf("FHD"), year = 2011), parseDisplayTitle("EN - Suits (FHD) (2011) (US)"))
        assertEquals(
            ParsedTitle("White Collar (2009)", "EN", listOf("FHD", "HEVC"), year = 2009),
            parseDisplayTitle("EN - White Collar (2009) (FHD HEVC)"),
        )
        assertEquals(
            ParsedTitle("Dune: Parte dos (2024)", "ES", listOf("HD"), year = 2024),
            parseDisplayTitle("ES - Dune: Parte dos [720p] (2024)"),
        )
        assertEquals(ParsedTitle("Better Call Saul", "NF", listOf("4K")), parseDisplayTitle("NF - Better Call Saul (4K)"))
    }

    @Test
    fun `superscript formats become tags and other marks stay`() {
        assertEquals(ParsedTitle("M+ LALIGA TV", "ES", listOf("4K")), parseDisplayTitle("ES| M+ LALIGA TV ᵁᴴᴰ ³⁸⁴⁰ᴾ"))
        assertEquals(ParsedTitle("NARUTO ᴿᴬᵂ", "JOYN", listOf("HD")), parseDisplayTitle("JOYN| NARUTO-HD ᴿᴬᵂ"))
        assertEquals(ParsedTitle("SKY CINEMA ◉", "WOW", listOf("HD")), parseDisplayTitle("WOW| SKY CINEMA ᴴᴰ ◉"))
        assertEquals(ParsedTitle("VIAPLAY SPORT", "SE", listOf("HD", "50 fps")), parseDisplayTitle("SE: VIAPLAY SPORT ᴴᴰ ⁵⁰ᶠᵖˢ"))
    }

    @Test
    fun `a code and a format joined by a space`() {
        assertEquals(ParsedTitle("Oppenheimer (2023)", "PL", listOf("4K"), year = 2023), parseDisplayTitle("PL 4K - Oppenheimer (2023)"))
    }

    @Test
    fun `whatever comes right before a pipe is the code`() {
        assertEquals(ParsedTitle("HOTSTAR", "IN-PREM", listOf("HD")), parseDisplayTitle("IN-PREM| HOTSTAR HD"))
        assertEquals(ParsedTitle("ALIBI", "UK-NOWTV", listOf("HD")), parseDisplayTitle("UK-NOWTV| ALIBI HD"))
        assertEquals(ParsedTitle("LA 1 ᴿᴬᵂ", "ES-C"), parseDisplayTitle("ES-C| LA 1 ᴿᴬᵂ"))
        assertEquals(ParsedTitle("3SAT ᴿᴬᵂ", "PRIME"), parseDisplayTitle("PRIME| 3SAT ᴿᴬᵂ"))
        assertEquals(ParsedTitle("NPO 1 ᴿᴬᵂ", "PLAY+"), parseDisplayTitle("PLAY+| NPO 1 ᴿᴬᵂ"))
        assertEquals(ParsedTitle("A&E", "U.S", listOf("HD")), parseDisplayTitle("U.S| A&E HD"))
        assertEquals(ParsedTitle("CINEMANIA GOLD", "HINDI", listOf("4K")), parseDisplayTitle("HINDI| CINEMANIA GOLD 4K"))
    }

    @Test
    fun `event lines keep their status`() {
        val raw = "Live | Fluminense - Palmeiras | Copa Libertadores"
        assertEquals(ParsedTitle(raw, null), parseDisplayTitle(raw))
    }

    @Test
    fun `two words for one code`() {
        assertEquals(ParsedTitle("Nur für Dich (1994)", "BLURAY-DE", year = 1994), parseDisplayTitle("BLURAY-DE - Nur für Dich (1994)"))
        assertEquals(ParsedTitle("Paris Cafe (2026)", "IN-TA", year = 2026), parseDisplayTitle("IN-TA - Paris Cafe (2026)"))
        assertEquals(ParsedTitle("01. Pulp Fiction (1994)", "DE-TOP", year = 1994), parseDisplayTitle("DE-TOP - 01. Pulp Fiction (1994)"))
    }

    @Test
    fun `the code twice is badged once`() {
        assertEquals(
            ParsedTitle("Jaula, The Chalk Line (2022)", "EN", listOf("SPANISH ENG-SUB"), year = 2022),
            parseDisplayTitle("EN - EN - Jaula, The Chalk Line (2022) (SPANISH ENG-SUB)"),
        )
    }

    @Test
    fun `audio and subtitles keep the provider's words`() {
        assertEquals(
            ParsedTitle("Head In The Stars (2023)", "EN", listOf("4K", "FRENCH ENG-SUB"), year = 2023),
            parseDisplayTitle("EN - Head In The Stars 4K (2023) (FRENCH ENG-SUB)"),
        )
        assertEquals(
            ParsedTitle("Beerus (2026)", "DE", listOf("Ger Sub"), year = 2026),
            parseDisplayTitle("DE - Beerus (2026) (JP) (Ger Sub)"),
        )
        assertEquals(ParsedTitle("Onihei", "FR", listOf("VOSTFR")), parseDisplayTitle("FR - Onihei (VOSTFR)"))
        assertEquals(
            ParsedTitle("La mission de Miss Hong (2026)", "FR", listOf("VOSTFR"), year = 2026),
            parseDisplayTitle("FR - La mission de Miss Hong (2026) (KR) VOSTFR"),
        )
        assertEquals(ParsedTitle("Yes Day", "SE", listOf("Svensk Tal")), parseDisplayTitle("SE - Yes Day (Svensk Tal)"))
        assertEquals(ParsedTitle("The Willoughbys", "SE", listOf("Svensk Text")), parseDisplayTitle("SE - The Willoughbys (Svensk Text)"))
        assertEquals(ParsedTitle("CARTOON NETWORK", "DK", listOf("MULTI-AUDIO")), parseDisplayTitle("DK| CARTOON NETWORK  [MULTI-AUDIO]"))
        assertEquals(ParsedTitle("Tut", "BG", listOf("BG-AUDIO")), parseDisplayTitle("BG - Tut (BG-AUDIO)"))
        assertEquals(
            ParsedTitle("Quelque Chose A Changé (2017)", "EN", listOf("SD", "FRENCH ONLY"), year = 2017),
            parseDisplayTitle("EN - Quelque Chose A Changé (2017) (FRENCH ONLY) (SD)"),
        )
        assertEquals(
            ParsedTitle("DZIEWCZYNY Z DUBAJU (2021)", "PL", listOf("FHD", "POLSKI"), year = 2021),
            parseDisplayTitle("PL - DZIEWCZYNY Z DUBAJU (2021) POLSKI 1080P"),
        )
        assertEquals(
            ParsedTitle("Zakręcony piątek 2 (2025)", "PL", listOf("4K", "DUBBING"), year = 2025),
            parseDisplayTitle("PL 4K - Zakręcony piątek 2 (2025) DUBBING"),
        )
        assertEquals(ParsedTitle("A Girl Walks Home (FESTIVAL MOVIE)", "IR"), parseDisplayTitle("IR - A Girl Walks Home (FESTIVAL MOVIE)"))
    }

    @Test
    fun `a timeshift keeps the provider's words`() {
        assertEquals(ParsedTitle("MTV LEBANON", "AR", listOf("+6H")), parseDisplayTitle("AR| MTV LEBANON +6H"))
    }

    @Test
    fun `events-only channels are marked`() {
        assertEquals(
            ParsedTitle("SKY SPORT 3", "DE", listOf("HD"), eventsOnly = true),
            parseDisplayTitle("DE| SKY SPORT 3 HD (NUR WÄHREND DER LIVE SPIELE)"),
        )
        assertEquals(ParsedTitle("M.LALIGA 3", "ES", listOf("FHD"), eventsOnly = true), parseDisplayTitle("ES| M.LALIGA 3 FHD [LIVEEVENT]"))
        assertEquals(ParsedTitle("SKY SPORT", "DE", listOf("4K"), eventsOnly = true), parseDisplayTitle("DE| SKY SPORT 4K [LIVE-EVENT]"))
        assertEquals(
            ParsedTitle("M.LALIGA 2", "ES", listOf("SD"), eventsOnly = true),
            parseDisplayTitle("ES| M.LALIGA 2 SD (SOLO EVENTOS)"),
        )
        assertEquals(
            ParsedTitle("NETFLIX 02 (FHD@60FPS) USA", "NF", eventsOnly = true),
            parseDisplayTitle("NF: NETFLIX 02 (FHD@60FPS) USA EVENTS ONLY"),
        )
        assertEquals(ParsedTitle("ESPN 3", "US", eventsOnly = true), parseDisplayTitle("US| ESPN 3 (LIVE DURING EVENTS ONLY)"))
        assertEquals(ParsedTitle("NBA 2", "US", eventsOnly = true), parseDisplayTitle("US| NBA 2 (DURING GAMES ONLY)"))
    }

    @Test
    fun `the year in brackets is read, wherever it is`() {
        val dune = parseDisplayTitle("AL - Dune: Part Two (2024)")
        assertEquals(2024, dune.year)
        assertEquals("Dune: Part Two (2024)", dune.title)
        assertEquals("Dune: Part Two", dune.titleWithoutYear)
        val longue = parseDisplayTitle("FR - Le Retour du roi (2003) VERSION LONGUE")
        assertEquals(2003, longue.year)
        assertEquals("Le Retour du roi VERSION LONGUE", longue.titleWithoutYear)
        assertEquals(
            "Jackie Brown TARANTINO, DE NIRO",
            parseDisplayTitle("EN - Jackie Brown 4K (1997) TARANTINO, DE NIRO").titleWithoutYear,
        )
        assertEquals(null, parseDisplayTitle("NF - The Order (2018-2020)").year)
        assertEquals(null, parseDisplayTitle("EN - Blade Runner 2049").year)
        assertEquals("(2019)", parseDisplayTitle("(2019)").titleWithoutYear)
    }

    @Test
    fun `a note right after the year is a tag, the words after it stay`() {
        assertEquals(
            ParsedTitle("Cocorico 2 (2026) CHRISTIAN CLAVIER, DIDIER BOURDON", "EN", listOf("FRENCH ENG-SUB"), year = 2026),
            parseDisplayTitle("EN - Cocorico 2 (2026) (FRENCH ENG-SUB) CHRISTIAN CLAVIER, DIDIER BOURDON"),
        )
        assertEquals(
            ParsedTitle("Along Came Auntie (1926) (B&W) LAUREL & HARDY", "EN", year = 1926),
            parseDisplayTitle("EN - Along Came Auntie (1926) (B&W) LAUREL & HARDY"),
        )
    }

    @Test
    fun `audio words and sinhronizirano are tags`() {
        assertEquals(
            ParsedTitle("The Shepherd (2023)", "BG", listOf("BG-AUDIO"), year = 2023),
            parseDisplayTitle("BG - The Shepherd (2023) BG-AUDIO"),
        )
        assertEquals(
            ParsedTitle("The Secret Sauce (2021)", "BG", listOf("BG.AUDIO"), year = 2021),
            parseDisplayTitle("BG - The Secret Sauce (2021) BG.AUDIO"),
        )
        assertEquals(
            ParsedTitle("Hoppers (2026)", "GR", listOf("GR-Audio"), year = 2026),
            parseDisplayTitle("GR - Hoppers (2026) GR-Audio"),
        )
        assertEquals(
            ParsedTitle("Ramba Oorvasi Menaka (2026)", "IN-TA", listOf("Multi.Audio"), year = 2026),
            parseDisplayTitle("IN-TA - Ramba Oorvasi Menaka (2026) Multi.Audio"),
        )
        assertEquals(
            ParsedTitle("Toy Story 5 (2026)", "EX", listOf("sinhronizirano"), year = 2026),
            parseDisplayTitle("EX - Toy Story 5 (2026) sinhronizirano"),
        )
    }

    @Test
    fun `a code the category already opens with is left out`() {
        val parsed = parseDisplayTitle("BG - Turbo (2013)")
        assertEquals(null, parsed.withoutCodeOf("BG - БЪЛГАРИЯ ДЕЦА").badge)
        assertEquals(null, parseDisplayTitle("DE| SKY SPORT 8 HD").withoutCodeOf("|DE| SPORT").badge)
        assertEquals("BG", parsed.withoutCodeOf("VOD - ALBANIA [AL]").badge)
        assertEquals("BG", parsed.withoutCodeOf(null).badge)
        assertEquals(listOf("HD"), parseDisplayTitle("DE| SKY SPORT 8 HD").withoutCodeOf("|DE| SPORT").tags)
    }

    @Test
    fun `words that are part of the title stay`() {
        assertEquals(ParsedTitle("Saw 3D (2010)", "NL", year = 2010), parseDisplayTitle("NL - Saw 3D (2010)"))
        assertEquals(ParsedTitle("WWE Raw (2023)", "EN", year = 2023), parseDisplayTitle("EN - WWE Raw (2023)"))
        assertEquals(ParsedTitle("TVN RAW", "PL"), parseDisplayTitle("PL| TVN RAW"))
        assertEquals(ParsedTitle("The Alps (2007) IMAX", "EN", year = 2007), parseDisplayTitle("EN - The Alps (2007) IMAX"))
        assertEquals(ParsedTitle("HD, AD HD اچ دی", "IR"), parseDisplayTitle("IR - HD, AD HD اچ دی"))
        assertEquals(ParsedTitle("NTSF:SD:SUV::", "EN"), parseDisplayTitle("EN - NTSF:SD:SUV:: (US)"))
        assertEquals(ParsedTitle("HD", null), parseDisplayTitle("HD"))
        assertEquals(ParsedTitle("TRON: Legacy (2010)", null, year = 2010), parseDisplayTitle("TRON: Legacy (2010)"))
        assertEquals(ParsedTitle("DOA: Dead or Alive (2006)", null, year = 2006), parseDisplayTitle("DOA: Dead or Alive (2006)"))
        assertEquals(ParsedTitle("HIT: The 2nd Case (2022)", null, year = 2022), parseDisplayTitle("HIT: The 2nd Case (2022)"))
    }

    @Test
    fun `episode title loses its series' raw name`() {
        assertEquals("S01E25", episodeTitleWithoutSeries("EN - The King of Queens - S01E25", "EN - The King of Queens"))
        assertEquals(
            "S03E04 - Whatever You Do",
            episodeTitleWithoutSeries("4K-A+ - Silo (2023) (US) - S03E04 - Whatever You Do", "4K-A+ - Silo (2023) (US)"),
        )
        assertEquals("Pilot", episodeTitleWithoutSeries("Pilot", "EN - The King of Queens"))
    }

    @Test
    fun `episode own title drops the show and the number`() {
        assertEquals("", episodeOwnTitle("EN - The King of Queens - S01E22"))
        assertEquals(
            "Whatever You Do, Don't Go Home",
            episodeOwnTitle("4K-A+ - Silo (2023) (US) - S03E04 - Whatever You Do, Don't Go Home"),
        )
        assertEquals("", episodeOwnTitle("S01E22"))
        assertEquals("The Body in the Library", episodeOwnTitle("The Body in the Library"))
        assertEquals("Pilot", episodeOwnTitle("Pilot"))
    }

    @Test
    fun `player episode name`() {
        assertEquals("", playerEpisodeName("EN - The King of Queens - S01E22"))
        assertEquals("Whatever You Do", playerEpisodeName("4K-A+ - Silo (2023) (US) - S03E04 - Whatever You Do"))
        assertEquals("Pilot", playerEpisodeName("EN - Show - Pilot"))
        assertEquals("Pilot", playerEpisodeName("Pilot"))
    }
}
