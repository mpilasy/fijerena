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
        assertEquals(ParsedTitle("The Perfect Lie (2026)", "NF", listOf("4K")), parseDisplayTitle("4K-NF - The Perfect Lie (2026) (NL)"))
        assertEquals(ParsedTitle("Silo (2023)", "A+", listOf("4K")), parseDisplayTitle("4K-A+ - Silo (2023) (US)"))
        assertEquals(ParsedTitle("Loki", "D+", listOf("4K")), parseDisplayTitle("4K-D+ - Loki"))
        assertEquals(ParsedTitle("Neagley (2026)", "AMZ", listOf("4K")), parseDisplayTitle("4K-AMZ - Neagley (2026) (US)"))
        assertEquals(ParsedTitle("Dune", "FR", listOf("4K", "HDR")), parseDisplayTitle("4K-FR-HDR - Dune"))
        assertEquals(ParsedTitle("Dune", "OSN+", listOf("4K")), parseDisplayTitle("4K-OSN+ - Dune"))
        assertEquals(ParsedTitle("Dune", "FR", listOf("4K")), parseDisplayTitle("4k-FR - Dune"))
        assertEquals(ParsedTitle("Dune", "DE", listOf("4K")), parseDisplayTitle("4K-DE  - Dune"))
        assertEquals(ParsedTitle("Skyscraper (2018)", "DE", listOf("4K", "DV")), parseDisplayTitle("4K-DE-DV - Skyscraper (2018)"))
        assertEquals(
            ParsedTitle("The Last of Us (2023)", "DE-BLURAY", listOf("4K", "DV")),
            parseDisplayTitle("DE-4K-BLURAY-DV - The Last of Us (2023) (US)"),
        )
        assertEquals(
            ParsedTitle("4 Blocks Zero (2026)", "DE", listOf("4K", "DV")),
            parseDisplayTitle("DE- [4K-DV-HDR] - 4 Blocks Zero (2026) (DE)"),
        )
        assertEquals(ParsedTitle("Heart Of The Beast (2026)", "CAM", listOf("SD")), parseDisplayTitle("SD/CAM - Heart Of The Beast (2026)"))
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
            ParsedTitle("A Knight of the Seven Kingdoms (2026)", "US", listOf("4K")),
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
        assertEquals(ParsedTitle("Titanic (1997)", "NL", listOf("HEVC")), parseDisplayTitle("NL - Titanic (1997) HEVC"))
        assertEquals(ParsedTitle("Scream 7 (2026)", "FR", listOf("LQ")), parseDisplayTitle("FR - Scream 7 (2026) LQ"))
    }

    @Test
    fun `formats before the year or in brackets become tags`() {
        assertEquals(ParsedTitle("Joker (2019)", "DE", listOf("4K")), parseDisplayTitle("DE - Joker 4K (2019) (4K)"))
        assertEquals(ParsedTitle("355 (2022)", "FR", listOf("4K")), parseDisplayTitle("FR - 355 4K (2022)"))
        assertEquals(ParsedTitle("Zeta (2026)", "ES", listOf("4K")), parseDisplayTitle("ES - Zeta [UHD] (2026)"))
        assertEquals(ParsedTitle("Un hijo (2026)", "ES", listOf("LQ")), parseDisplayTitle("ES - Un hijo (LQ) (2026)"))
        assertEquals(ParsedTitle("Suits (2011)", "EN", listOf("FHD")), parseDisplayTitle("EN - Suits (FHD) (2011) (US)"))
        assertEquals(
            ParsedTitle("White Collar (2009)", "EN", listOf("FHD", "HEVC")),
            parseDisplayTitle("EN - White Collar (2009) (FHD HEVC)"),
        )
        assertEquals(ParsedTitle("Dune: Parte dos (2024)", "ES", listOf("HD")), parseDisplayTitle("ES - Dune: Parte dos [720p] (2024)"))
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
        assertEquals(ParsedTitle("Oppenheimer (2023)", "PL", listOf("4K")), parseDisplayTitle("PL 4K - Oppenheimer (2023)"))
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
        assertEquals(ParsedTitle("Nur für Dich (1994)", "BLURAY-DE"), parseDisplayTitle("BLURAY-DE - Nur für Dich (1994)"))
        assertEquals(ParsedTitle("Paris Cafe (2026)", "IN-TA"), parseDisplayTitle("IN-TA - Paris Cafe (2026)"))
        assertEquals(ParsedTitle("01. Pulp Fiction (1994)", "DE-TOP"), parseDisplayTitle("DE-TOP - 01. Pulp Fiction (1994)"))
    }

    @Test
    fun `the code twice is badged once`() {
        assertEquals(
            ParsedTitle("Jaula, The Chalk Line (2022)", "EN", listOf("SPANISH ENG-SUB")),
            parseDisplayTitle("EN - EN - Jaula, The Chalk Line (2022) (SPANISH ENG-SUB)"),
        )
    }

    @Test
    fun `audio and subtitles keep the provider's words`() {
        assertEquals(
            ParsedTitle("Head In The Stars (2023)", "EN", listOf("4K", "FRENCH ENG-SUB")),
            parseDisplayTitle("EN - Head In The Stars 4K (2023) (FRENCH ENG-SUB)"),
        )
        assertEquals(ParsedTitle("Beerus (2026)", "DE", listOf("Ger Sub")), parseDisplayTitle("DE - Beerus (2026) (JP) (Ger Sub)"))
        assertEquals(ParsedTitle("Onihei", "FR", listOf("VOSTFR")), parseDisplayTitle("FR - Onihei (VOSTFR)"))
        assertEquals(
            ParsedTitle("La mission de Miss Hong (2026)", "FR", listOf("VOSTFR")),
            parseDisplayTitle("FR - La mission de Miss Hong (2026) (KR) VOSTFR"),
        )
        assertEquals(ParsedTitle("Yes Day", "SE", listOf("Svensk Tal")), parseDisplayTitle("SE - Yes Day (Svensk Tal)"))
        assertEquals(ParsedTitle("The Willoughbys", "SE", listOf("Svensk Text")), parseDisplayTitle("SE - The Willoughbys (Svensk Text)"))
        assertEquals(ParsedTitle("CARTOON NETWORK", "DK", listOf("MULTI-AUDIO")), parseDisplayTitle("DK| CARTOON NETWORK  [MULTI-AUDIO]"))
        assertEquals(ParsedTitle("Tut", "BG", listOf("BG-AUDIO")), parseDisplayTitle("BG - Tut (BG-AUDIO)"))
        assertEquals(
            ParsedTitle("Quelque Chose A Changé (2017)", "EN", listOf("SD", "FRENCH ONLY")),
            parseDisplayTitle("EN - Quelque Chose A Changé (2017) (FRENCH ONLY) (SD)"),
        )
        assertEquals(
            ParsedTitle("DZIEWCZYNY Z DUBAJU (2021)", "PL", listOf("FHD", "POLSKI")),
            parseDisplayTitle("PL - DZIEWCZYNY Z DUBAJU (2021) POLSKI 1080P"),
        )
        assertEquals(
            ParsedTitle("Zakręcony piątek 2 (2025)", "PL", listOf("4K", "DUBBING")),
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
    fun `words that are part of the title stay`() {
        assertEquals(ParsedTitle("Saw 3D (2010)", "NL"), parseDisplayTitle("NL - Saw 3D (2010)"))
        assertEquals(ParsedTitle("WWE Raw (2023)", "EN"), parseDisplayTitle("EN - WWE Raw (2023)"))
        assertEquals(ParsedTitle("TVN RAW", "PL"), parseDisplayTitle("PL| TVN RAW"))
        assertEquals(ParsedTitle("The Alps (2007) IMAX", "EN"), parseDisplayTitle("EN - The Alps (2007) IMAX"))
        assertEquals(ParsedTitle("HD, AD HD اچ دی", "IR"), parseDisplayTitle("IR - HD, AD HD اچ دی"))
        assertEquals(ParsedTitle("NTSF:SD:SUV::", "EN"), parseDisplayTitle("EN - NTSF:SD:SUV:: (US)"))
        assertEquals(ParsedTitle("HD", null), parseDisplayTitle("HD"))
        assertEquals(ParsedTitle("TRON: Legacy (2010)", null), parseDisplayTitle("TRON: Legacy (2010)"))
        assertEquals(ParsedTitle("DOA: Dead or Alive (2006)", null), parseDisplayTitle("DOA: Dead or Alive (2006)"))
        assertEquals(ParsedTitle("HIT: The 2nd Case (2022)", null), parseDisplayTitle("HIT: The 2nd Case (2022)"))
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
