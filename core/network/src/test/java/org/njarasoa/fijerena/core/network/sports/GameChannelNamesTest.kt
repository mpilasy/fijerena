package org.njarasoa.fijerena.core.network.sports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.njarasoa.fijerena.core.network.sports.GameChannelFixtures.broadcaster
import org.njarasoa.fijerena.core.network.sports.GameChannelFixtures.game
import org.njarasoa.fijerena.core.network.sports.GameChannelFixtures.team
import org.njarasoa.fijerena.core.network.sports.TeamNameForms.Hit

/** Team, competition and channel names of the game matcher: whole words, any accent or case, short names never alone. */
class GameChannelNamesTest {
    private fun hit(
        team: SportsTeam,
        title: String,
        description: String? = null,
    ) = TeamNameForms.of(team).hit(SportsText.padded(title), SportsText.padded(description))

    @Test
    fun `fold removes accents, case and punctuation`() {
        assertEquals("paris saint germain", SportsText.fold("Paris Saint-Germain"))
        assertEquals("atletico madrid", SportsText.fold("Atlético Madrid"))
        assertEquals("bayern munchen", SportsText.fold("Bayern München"))
        assertEquals("barca", SportsText.fold("Barça"))
        assertEquals("brighton and hove albion", SportsText.fold("Brighton & Hove Albion"))
        assertEquals("bmouth", SportsText.fold("B'mouth"))
        assertEquals("saint pauli", SportsText.fold("St. Pauli"))
        assertEquals("canal plus sport", SportsText.fold("CANAL+ SPORT"))
    }

    @Test
    fun `names match whole words only`() {
        val inter = team("Internazionale", "INT", shortName = "Inter Milan")
        assertEquals(Hit.NONE, hit(inter, "Interview: the coach"))
        assertEquals(Hit.NONE, hit(inter, "Rugby International"))
        assertEquals(Hit.WEAK, hit(inter, "L'Heure Inter"))
        assertEquals(Hit.DISTINCTIVE, hit(inter, "AC Milan / Inter Milan"))
    }

    @Test
    fun `a short or common name is weak, and counts only in the title`() {
        val city = team("Manchester City", "MNC", shortName = "Man City")
        assertEquals(Hit.NONE, hit(city, "City Life"))
        assertEquals(Hit.DISTINCTIVE, hit(city, "PL: Man City v Brighton"))

        val psg = team("Paris Saint-Germain", "PSG")
        assertEquals(Hit.WEAK, hit(psg, "PSG / OM"))
        assertEquals(Hit.NONE, hit(psg, "Ligue 1", "Le PSG reçoit l'OM."))
        assertEquals(Hit.DISTINCTIVE, hit(psg, "Ligue 1", "Le Paris Saint-Germain reçoit l'OM."))
    }

    @Test
    fun `a team's city is weak, its nickname is distinctive`() {
        val chiefs = team("Kansas City Chiefs", "Kansas City", "KC", shortName = "Chiefs", sport = "football")
        val forms = TeamNameForms.of(chiefs)
        assertTrue("kansas city" in forms.weak)
        assertTrue("kc" in forms.weak)
        assertTrue("chiefs" in forms.distinctive)
        assertTrue("kc chiefs" in forms.distinctive)
        assertEquals(Hit.WEAK, hit(chiefs, "Kansas City Royals at Detroit Tigers"))
    }

    @Test
    fun `aliases and shortened names find the same team`() {
        val united = team("Manchester United", "MUN")
        assertEquals(Hit.DISTINCTIVE, hit(united, "PL HL 26/27: Chelsea v Man Utd"))
        assertEquals(Hit.DISTINCTIVE, hit(united, "PLTV: Mic'd Up '26 Rodrigo B v ManU"))
        assertEquals(Hit.DISTINCTIVE, hit(team("Tottenham Hotspur", "TOT"), "Retro EFL Cup 15: Chelsea v Spurs"))
        assertEquals(Hit.DISTINCTIVE, hit(team("Bayern Munich", "BAY"), "Bayern München – Borussia Dortmund"))
        assertEquals(Hit.DISTINCTIVE, hit(team("Real Madrid", "RMA"), "LaLiga 26/27: Barcelona v R Madrid"))
        assertEquals(Hit.DISTINCTIVE, hit(team("Atlético Madrid", "ATM"), "LaLiga 26/27: Levante v A Madrid"))
        assertEquals(Hit.DISTINCTIVE, hit(team("Crystal Palace", "CRY"), "PL 26/27: Tottenham v C Palace"))
        assertEquals(Hit.DISTINCTIVE, hit(team("1. FC Köln", "KOE"), "U. a.: 1. FC Union Berlin – 1. FC Köln."))
        assertEquals(Hit.DISTINCTIVE, hit(team("AFC Bournemouth", "BOU"), "PL HL 26/27: B'mouth v Leeds"))
        assertEquals(
            Hit.DISTINCTIVE,
            hit(team("Los Angeles Lakers", "LAL", shortName = "Lakers", sport = "basketball"), "LA Lakers vs Celtics"),
        )
        assertEquals(Hit.NONE, hit(team("Real Madrid", "RMA"), "LaLiga 26/27: A Madrid v Deportivo"))
    }

    @Test
    fun `competitions are found without their country word`() {
        val forms = CompetitionForms.of(SportsLeague(SportsSource.ESPN, "eng.1", "English Premier League", "soccer"))
        assertTrue("premier league" in forms)
        assertTrue("pl" in forms)
        val ucl = CompetitionForms.of(SportsLeague(SportsSource.ESPN, "soccer/uefa.champions", "UEFA Champions League", "soccer"))
        assertTrue("ligue des champions" in ucl)
        val bundesliga = CompetitionForms.of(SportsLeague(SportsSource.ESPN, "ger.1", "German Bundesliga", "soccer"))
        assertTrue("bundesliga" in bundesliga)
    }

    @Test
    fun `replays, highlights and other seasons are not the game`() {
        val text = GameText(game("2026-11-01T16:30:00Z", team("Liverpool"), team("Arsenal"), "Premier League"))
        listOf(
            "EFL Cup Hlts: Liverpool v Chelsea",
            "PL HL 26/27: Chelsea v Man Utd",
            "R12: Al Nassr vs Al Khloud (HL)",
            "EFL 17/18: Arsenal v Man City",
            "PL Classic 15/16: Liverpool v Arsenal",
            "2008 UCL Final: v Chelsea",
            "Retro L1 99: Gillingham v Man City",
            "Fußball: Bundesliga Kompakt",
            "Terminé : Stade Français Paris / La Rochelle",
            "Next game: Chicago Bears at Seattle Seahawks at 11/02/2026 08:15 PM EST",
            "PL: Liverpool v Arsenal Highlights",
        ).forEach { assertTrue(it, text.isReplay(it)) }
        listOf(
            "PL 26/27: Chelsea v Man Utd",
            "Live PL: Liverpool v Arsenal",
            "Football : Ligue 1 McDonald's 2026/2027",
            "Live NFL Gameday Morning 2026/27",
            "A venir : Stade Français Paris / La Rochelle",
        ).forEach { assertFalse(it, text.isReplay(it)) }
    }

    @Test
    fun `channel country tags are read and stripped`() {
        val cases =
            mapOf(
                "FR: CANAL+ SPORT 360 FHD" to ("FR" to "canal plus sport 360"),
                "UK| Sky Sports Main Event HD" to ("GB" to "sky sport main event"),
                "|UK| Sky Sports+ HD" to ("GB" to "sky sport plus"),
                "US - ESPN 2" to ("US" to "espn 2"),
                "AR: beIN SPORTS 1" to (null to "bein sport 1"),
                "beIN Sports 1 (FR)" to ("FR" to "bein sport 1"),
                "TF1 HD" to (null to "tf 1"),
                "BBC One" to (null to "bbc one"),
                "ZA: SS Premier League HD" to ("ZA" to "supersport premier league"),
            )
        cases.forEach { (name, expected) ->
            val parsed = BroadcasterChannels.parse(name)
            assertEquals(name, expected, parsed.country to parsed.words.joinToString(" "))
        }
    }

    @Test
    fun `a broadcaster matches its channels in its own country`() {
        fun matches(
            b: Broadcaster,
            channel: String,
        ) = BroadcasterChannels.matches(b, BroadcasterChannels.broadcasterTokens(b.name), BroadcasterChannels.parse(channel))

        val canalSport = broadcaster("Canal+ Sport", "FR")
        assertTrue(matches(canalSport, "FR: CANAL+ SPORT 360 FHD"))
        assertTrue(matches(canalSport, "C+ Sport HD"))
        assertTrue(matches(canalSport, "AFR: Canal+ Sport 1"))
        assertFalse(matches(canalSport, "PL: Canal+ Sport 2"))
        assertFalse(matches(canalSport, "FR: CANAL+ CINEMA"))

        val bein1 = broadcaster("beIN Sports 1", "FR")
        assertTrue(matches(bein1, "|FR| beIN 1 HD"))
        assertTrue(matches(bein1, "AR: beIN SPORTS 1"))
        assertFalse(matches(bein1, "US: beIN Sports 1"))
        assertFalse(matches(bein1, "FR: beIN SPORTS 2"))
        assertFalse(matches(broadcaster("beIN Sports 1", "FR"), "FR: beIN SPORTS 10"))

        assertTrue(matches(broadcaster("SuperSport Premier League", "ZA"), "SS Premier League HD"))
        assertTrue(matches(broadcaster("Sky Sports", "GB"), "UK| Sky Sports Main Event HD"))
        assertFalse(matches(broadcaster("Sky Sports", "GB"), "DE: Sky Sport Bundesliga"))
        assertFalse(matches(broadcaster("Sky Sports", "GB"), "UK: Sky News"))
        assertTrue(matches(broadcaster("TNT Sports", "GB"), "UK: BT Sport 1"))
        assertTrue(matches(broadcaster("La chaîne L'Équipe", "FR"), "FR: L'EQUIPE HD"))
        assertFalse(matches(broadcaster("ESPN+", "US"), "US: ESPN"))
    }
}
