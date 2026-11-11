package org.njarasoa.fijerena.core.ui.legal

import androidx.annotation.StringRes
import org.njarasoa.fijerena.core.ui.R

/**
 * Settings → About → About Fijerena: the project's web pages, the same on TV and phone. A row
 * opens a dialog with the page's QR code, so a TV without a browser still gets there.
 */
enum class ProjectLink(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val url: String,
) {
    SOURCE_CODE(R.string.about_source_code, R.string.about_source_code_desc, "https://github.com/mpilasy/fijerena"),

    // The asset names carry the version, so only the release page itself has a lasting address.
    LATEST_RELEASE(
        R.string.about_latest_release,
        R.string.about_latest_release_desc,
        "https://github.com/mpilasy/fijerena/releases/latest",
    ),
    PRIVACY(R.string.about_privacy, R.string.about_privacy_desc, "https://github.com/mpilasy/fijerena/blob/main/PRIVACY.md"),
    REPORT_PROBLEM(R.string.about_report_problem, R.string.about_report_problem_desc, "https://github.com/mpilasy/fijerena/issues"),
    ;

    /** The address as a row and the dialog show it: without "https://". */
    val shownUrl: String get() = url.removePrefix("https://")
}
