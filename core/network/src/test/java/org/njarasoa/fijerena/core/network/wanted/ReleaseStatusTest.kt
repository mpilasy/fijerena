package org.njarasoa.fijerena.core.network.wanted

import org.junit.Assert.assertEquals
import org.junit.Test
import org.njarasoa.fijerena.core.network.tmdb.TmdbRecommendation
import org.njarasoa.fijerena.core.network.tmdb.TmdbReleaseDateEntry
import org.njarasoa.fijerena.core.network.tmdb.TmdbReleaseDatesCountry
import org.njarasoa.fijerena.core.network.tmdb.TmdbReleaseDatesResponse
import org.njarasoa.fijerena.core.player.domain.ContentType
import java.time.LocalDate

/**
 * A title no source has: Coming, Starts, In cinemas, Out on digital or just out, from TMDB's
 * release dates of the device's region. See the wishlist ux plan, Phase 3.
 */
class ReleaseStatusTest {
    private val today = LocalDate.parse("2026-10-22")

    private fun film(
        release: String?,
        digital: String? = null,
        cinemas: Boolean = true,
    ) = ReleaseStatus.of(ContentType.MOVIES, ReleaseInfo(release, digital, cinemas), today)

    @Test
    fun `a film not out yet is coming, with its date`() {
        assertEquals(ReleaseStatus(ReleaseStage.COMING, LocalDate.parse("2027-03-14")), film("2027-03-14"))
    }

    @Test
    fun `a film in cinemas says so, with its digital date when known`() {
        assertEquals(ReleaseStatus(ReleaseStage.IN_CINEMAS), film("2026-10-01"))
        assertEquals(ReleaseStatus(ReleaseStage.IN_CINEMAS, LocalDate.parse("2026-12-02")), film("2026-10-01", "2026-12-02"))
    }

    @Test
    fun `a film out on digital in the last 90 days is just out, older is out`() {
        assertEquals(ReleaseStage.OUT_ON_DIGITAL, film("2026-06-01", "2026-08-01").stage)
        assertEquals(ReleaseStage.OUT, film("1994-09-23", "1999-03-09").stage)
    }

    @Test
    fun `an old cinema release with no digital date is out, not in cinemas`() {
        assertEquals(ReleaseStage.OUT, film("1994-09-23").stage)
    }

    @Test
    fun `nothing known is out`() {
        assertEquals(ReleaseStage.OUT, film(null, cinemas = false).stage)
    }

    @Test
    fun `a show not started yet starts on its date, else it is out`() {
        assertEquals(
            ReleaseStatus(ReleaseStage.STARTS, LocalDate.parse("2027-05-05")),
            ReleaseStatus.of(ContentType.TV_SHOWS, ReleaseInfo("2027-05-05"), today),
        )
        assertEquals(ReleaseStage.OUT, ReleaseStatus.of(ContentType.TV_SHOWS, ReleaseInfo("2020-01-01"), today).stage)
    }

    @Test
    fun `a film's dates come from the region, else from everywhere`() {
        val response =
            TmdbReleaseDatesResponse(
                listOf(
                    TmdbReleaseDatesCountry("US", listOf(entry("2021-10-22", 3), entry("2021-10-22", 4))),
                    TmdbReleaseDatesCountry("FR", listOf(entry("2021-09-15", 3), entry("2022-01-19", 5))),
                ),
            )

        assertEquals(ReleaseInfo("2021-09-15", "2022-01-19", true), ReleaseStatus.ofMovie(response, "FR"))
        assertEquals(ReleaseInfo("2021-09-15", "2021-10-22", true), ReleaseStatus.ofMovie(response, "MG"))
    }

    @Test
    fun `a show's release is its first air date and status`() {
        val details = TmdbRecommendation(id = 1, firstAirDate = "2027-05-05", status = "In Production")

        assertEquals(ReleaseInfo("2027-05-05", showStatus = "In Production"), ReleaseStatus.ofShow(details))
    }

    private fun entry(
        day: String,
        type: Int,
    ) = TmdbReleaseDateEntry(releaseDate = "${day}T00:00:00.000Z", type = type)
}
