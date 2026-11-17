package org.njarasoa.fijerena.core.ui.legal

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import org.njarasoa.fijerena.core.ui.R

/** A web address of About Fijerena that a dialog shows as a QR code, so a TV without a browser still gets there. */
sealed interface QrLink {
    @get:StringRes val titleRes: Int

    @get:StringRes val descriptionRes: Int
    val url: String

    /** The address as a row and the dialog show it: without "https://". */
    val shownUrl: String
}

/**
 * Settings → About → About Fijerena: the project's web pages, the same on TV and phone. A row
 * opens a dialog with the page's QR code.
 */
enum class ProjectLink(
    @StringRes override val titleRes: Int,
    @StringRes override val descriptionRes: Int,
    override val url: String,
) : QrLink {
    SOURCE_CODE(R.string.about_source_code, R.string.about_source_code_desc, "https://github.com/mpilasy/fijerena"),
    PRIVACY(R.string.about_privacy, R.string.about_privacy_desc, "https://github.com/mpilasy/fijerena/blob/main/PRIVACY.md"),
    REPORT_PROBLEM(R.string.about_report_problem, R.string.about_report_problem_desc, "https://github.com/mpilasy/fijerena/issues"),
    ;

    override val shownUrl: String get() = url.removePrefix("https://")
}

/**
 * Settings → About → About Fijerena → Get the App: the "Get it on" badges, to pass the apps to
 * another person, as in the README. An Obtainium badge adds the phone app or the TV app with an
 * APK filter, since each release has both APKs. The phone opens GitHub's page at once; every other
 * badge shows its QR code first.
 */
enum class AppDownload(
    @DrawableRes val badge: Int,
    @StringRes val badgeDescriptionRes: Int,
    @StringRes val captionRes: Int,
    @StringRes override val titleRes: Int,
    @StringRes override val descriptionRes: Int,
    override val url: String,
    override val shownUrl: String,
) : QrLink {
    OBTAINIUM_PHONE(
        R.drawable.badge_obtainium,
        R.string.about_badge_obtainium,
        R.string.about_badge_caption_phone,
        R.string.about_get_phone_obtainium,
        R.string.about_get_obtainium_desc,
        obtainiumUrl("org.njarasoa.fijerena.mobile", "Fijerena", "fijerena-phone"),
        OBTAINIUM_HOST,
    ),
    OBTAINIUM_TV(
        R.drawable.badge_obtainium,
        R.string.about_badge_obtainium,
        R.string.about_badge_caption_tv,
        R.string.about_get_tv_obtainium,
        R.string.about_get_obtainium_desc,
        obtainiumUrl("org.njarasoa.fijerena", "Fijerena%20TV", "fijerena-tv"),
        OBTAINIUM_HOST,
    ),

    // The asset names carry the version, so only the release page itself has a lasting address.
    GITHUB(
        R.drawable.badge_github,
        R.string.about_badge_github,
        R.string.about_badge_caption_both,
        R.string.about_latest_release,
        R.string.about_latest_release_desc,
        "https://github.com/mpilasy/fijerena/releases/latest",
        "github.com/mpilasy/fijerena/releases",
    ),
}

private const val OBTAINIUM_HOST = "apps.obtainium.imranr.dev"

/**
 * Obtainium's redirect page with `obtainium://app/{app}`, encoded twice as in the README: the
 * page decodes `r` once and opens the link, and Obtainium decodes the app's JSON. [encodedName] is
 * the name, URL-encoded once.
 */
private fun obtainiumUrl(
    id: String,
    encodedName: String,
    apkFilter: String,
): String {
    val app =
        "%7B%22id%22%3A%22$id%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fmpilasy%2Ffijerena%22%2C%22author%22%3A%22mpilasy%22" +
            "%2C%22name%22%3A%22$encodedName%22%2C%22additionalSettings%22%3A%22%7B%5C%22apkFilterRegEx%5C%22%3A%5C%22$apkFilter%5C%22%7D%22%7D"
    return "https://$OBTAINIUM_HOST/redirect?r=" + ("obtainium://app/$app").replace("%", "%25").replace(":", "%3A").replace("/", "%2F")
}
