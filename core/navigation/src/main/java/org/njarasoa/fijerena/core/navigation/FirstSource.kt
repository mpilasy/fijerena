package org.njarasoa.fijerena.core.navigation

/*
 * The admin's first run (the settings flow review, E1): with no source at all, an Expert in use
 * starts on Add Source, with Settings under it (Back goes to Settings); the first source saved
 * opens Home with the back stack cleared. A profile that is not Expert starts on Home, which tells
 * it to ask a grown-up for a source.
 */

/** With [hasSource] false and an Expert in use ([expert]), the app starts on Add Source over Settings. */
fun startsOnAddSource(
    hasSource: Boolean,
    expert: Boolean,
): Boolean = !hasSource && expert

/**
 * A save on Add Source opens Home with the back stack cleared: a new source ([editId] < 0) when
 * there was no source before it ([sourcesBefore] 0). A later new source or an edit goes back as before.
 */
fun opensHomeAfterSave(
    editId: Long,
    sourcesBefore: Int,
): Boolean = editId < 0 && sourcesBefore == 0
