package org.njarasoa.fijerena.core.player

/**
 * A provider answer saved for the tests, under `src/test/resources/fixtures/`, for example
 * `fixture("xtream/var-get_vod_info-info-empty-array.json")`. A `rec-` file is a recorded answer,
 * cleaned and trimmed; a `var-` file is written by hand to show one quirk.
 */
internal fun fixture(path: String): String =
    requireNotNull(object {}.javaClass.classLoader?.getResource("fixtures/$path")) { "No fixture $path" }.readText()
