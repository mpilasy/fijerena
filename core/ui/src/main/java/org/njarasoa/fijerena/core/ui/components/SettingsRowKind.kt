package org.njarasoa.fijerena.core.ui.components

/**
 * What a Settings row does on a tap or OK, the same on both apps: the row shows the chevron (›)
 * for [Navigation] only.
 */
enum class SettingsRowKind {
    /** Opens a page, a picker or a dialog to choose in: the row ends with a chevron. */
    Navigation,

    /** Runs at once (Refresh Ranking, Export), after a confirm (Shrink Database), or only shows a value: no chevron. */
    Action,
}
