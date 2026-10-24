package org.njarasoa.fijerena.core.ui.model

import org.njarasoa.fijerena.core.player.domain.ProviderType

/**
 * Source types that can't work yet: SMB can't play (no `smb://` DataSource) and Local has no folder
 * picker, so it saves an empty config. the next level rock solid resilience plan
 * → R-22.
 */
private val DEV_ONLY_SOURCE_TYPES = setOf(ProviderType.SMB, ProviderType.LOCAL)

/**
 * The types Add Source offers: all of them in a debug build, otherwise all but SMB and Local.
 * Expert mode does not change this: it is the admin's switch now, and an admin must not see types
 * that do not work. The type of a source being edited ([editedType]) is always kept, so an
 * existing SMB or Local source can still be edited.
 */
fun addSourceTypes(
    debugBuild: Boolean,
    editedType: ProviderType? = null,
): List<ProviderType> = ProviderType.entries.filter { debugBuild || it == editedType || it !in DEV_ONLY_SOURCE_TYPES }
