package org.njarasoa.fijerena.core.ui.model

import org.njarasoa.fijerena.core.player.domain.ProviderType

/**
 * Source types that can't work yet: SMB can't play (no `smb://` DataSource) and Local has no folder
 * picker, so it saves an empty config. docs/plans/archive/20261002_next-level-rock-solid-resilience-plan.md
 * → R-22.
 */
private val DEV_ONLY_SOURCE_TYPES = setOf(ProviderType.SMB, ProviderType.LOCAL)

/**
 * The types Add Source offers: all of them in developer mode, otherwise all but SMB and Local. The
 * type of a source being edited ([editedType]) is always kept, so an existing SMB or Local source
 * can still be edited.
 */
fun addSourceTypes(
    isDevMode: Boolean,
    editedType: ProviderType? = null,
): List<ProviderType> = ProviderType.entries.filter { isDevMode || it == editedType || it !in DEV_ONLY_SOURCE_TYPES }
