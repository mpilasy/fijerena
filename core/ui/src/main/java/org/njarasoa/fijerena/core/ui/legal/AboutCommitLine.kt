package org.njarasoa.fijerena.core.ui.legal

/**
 * About's line under the version, the same on TV and phone: the build's commit and the commit's
 * date in UTC ("217db273b · 2026-11-12 14:07 UTC"). Both come from the build (`GIT_HASH`,
 * `COMMIT_TIME`), so a release built anywhere shows the same line.
 */
fun aboutCommitLine(
    hash: String,
    commitTime: String,
): String = "$hash · $commitTime"
