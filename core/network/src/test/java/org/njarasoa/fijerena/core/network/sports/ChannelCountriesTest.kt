package org.njarasoa.fijerena.core.network.sports

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelCountriesTest {
    private fun channel(name: String) = ChannelCountries.channelCountries(listOf(name), emptyList())

    private fun category(name: String) = ChannelCountries.channelCountries(emptyList(), listOf(name))

    @Test
    fun `channel tags give their country`() {
        assertEquals(setOf("FR"), channel("FR: TF1 HD"))
        assertEquals(setOf("GB"), channel("UK| Sky Sports Main Event"))
        assertEquals(setOf("GB"), channel("|UK| BBC One"))
        assertEquals(setOf("US"), channel("US - ESPN"))
        assertEquals(setOf("FR"), channel("FR | Canal+ Sport"))
        assertEquals(setOf("DE"), channel("DE ▎ Sky Sport"))
        assertEquals(setOf("ES"), channel("ES ▎ DAZN LaLiga"))
        assertEquals(setOf("DE"), channel("[DE] Sky Sport Bundesliga"))
        assertEquals(setOf("GB"), channel("BBC One (UK)"))
        assertEquals(setOf("BR"), channel("🇧🇷 SporTV"))
        assertEquals(setOf("GB"), channel("🇬🇧 Sky Sports F1"))
    }

    @Test
    fun `language and region tags give no country`() {
        assertEquals(emptySet<String>(), channel("|AR| MBC 1"))
        assertEquals(emptySet<String>(), channel("AR - beIN Sports 1"))
        assertEquals(emptySet<String>(), channel("EN: Discovery"))
        assertEquals(emptySet<String>(), channel("AFR | SuperSport Football"))
        assertEquals(emptySet<String>(), channel("ML | Asianet"))
        assertEquals(emptySet<String>(), channel("Eurosport 1 HD"))
        assertEquals(emptySet<String>(), channel("France 2"))
    }

    @Test
    fun `categories give their country by tag or name`() {
        assertEquals(setOf("FR"), category("FR | SPORTS"))
        assertEquals(setOf("FR"), category("France"))
        assertEquals(setOf("GB"), category("United Kingdom"))
        assertEquals(setOf("GB"), category("UK Sports"))
        assertEquals(setOf("DE"), category("Deutschland HD"))
        assertEquals(setOf("BR"), category("Brasil"))
        assertEquals(setOf("ZA"), category("South Africa"))
        assertEquals(setOf("ZA"), category("Afrique du Sud"))
        assertEquals(setOf("AR"), category("Argentina"))
    }

    @Test
    fun `regions, languages and common words in categories give no country`() {
        assertEquals(emptySet<String>(), category("Afrique"))
        assertEquals(emptySet<String>(), category("Arabic"))
        assertEquals(emptySet<String>(), category("AR | Sports"))
        assertEquals(emptySet<String>(), category("ARAB"))
        assertEquals(emptySet<String>(), category("Sports"))
        assertEquals(emptySet<String>(), category("Georgia Local"))
    }

    @Test
    fun `countries come most frequent first, at most 30`() {
        val names = listOf("UK| Sky 1", "FR: TF1", "UK| Sky 2", "US - ESPN", "UK| BBC")
        assertEquals(listOf("GB", "FR", "US"), ChannelCountries.channelCountries(names, listOf("FR | SPORTS")).toList())
        val many =
            java.util.Locale
                .getISOCountries()
                .take(40)
                .map { "$it: Channel" }
        assertEquals(ChannelCountries.MAX_COUNTRIES, ChannelCountries.channelCountries(many, emptyList()).size)
    }
}
