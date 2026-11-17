package org.njarasoa.fijerena.core.ui.legal

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.URI
import java.net.URLDecoder

/** The Obtainium badges add the right app with the right APK filter. */
class AppDownloadTest {
    // What Obtainium gets: the redirect page's `r`, decoded once by the page, then the app's JSON.
    private fun obtainiumApp(download: AppDownload): String {
        val r = URI(download.url).rawQuery.removePrefix("r=")
        val link = URLDecoder.decode(r, "UTF-8")
        return URLDecoder.decode(link.removePrefix("obtainium://app/"), "UTF-8")
    }

    @Test
    fun `the phone badge adds the phone app with the phone APK`() {
        assertEquals(
            """{"id":"org.njarasoa.fijerena.mobile","url":"https://github.com/mpilasy/fijerena","author":"mpilasy","name":"Fijerena",""" +
                """"additionalSettings":"{\"apkFilterRegEx\":\"fijerena-phone\"}"}""",
            obtainiumApp(AppDownload.OBTAINIUM_PHONE),
        )
    }

    @Test
    fun `the TV badge adds the TV app with the TV APK`() {
        assertEquals(
            """{"id":"org.njarasoa.fijerena","url":"https://github.com/mpilasy/fijerena","author":"mpilasy","name":"Fijerena TV",""" +
                """"additionalSettings":"{\"apkFilterRegEx\":\"fijerena-tv\"}"}""",
            obtainiumApp(AppDownload.OBTAINIUM_TV),
        )
    }

    @Test
    fun `the links are encoded as Obtainium's own badge links`() {
        assertEquals(
            "https://apps.obtainium.imranr.dev/redirect?r=obtainium%3A%2F%2Fapp%2F" +
                "%257B%2522id%2522%253A%2522org.njarasoa.fijerena%2522%252C" +
                "%2522url%2522%253A%2522https%253A%252F%252Fgithub.com%252Fmpilasy%252Ffijerena%2522%252C" +
                "%2522author%2522%253A%2522mpilasy%2522%252C%2522name%2522%253A%2522Fijerena%2520TV%2522%252C" +
                "%2522additionalSettings%2522%253A%2522%257B%255C%2522apkFilterRegEx%255C%2522%253A" +
                "%255C%2522fijerena-tv%255C%2522%257D%2522%257D",
            AppDownload.OBTAINIUM_TV.url,
        )
    }
}
