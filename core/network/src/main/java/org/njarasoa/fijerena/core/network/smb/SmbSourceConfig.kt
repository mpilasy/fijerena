package org.njarasoa.fijerena.core.network.smb

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The `config` JSON of an SMB source, as `MediaProviderFactory.createSmb` reads it. Built rather
 * than interpolated, so a `"` or `\` in the host or share stays valid JSON.
 * docs/plans/archive/20261002_next-level-rock-solid-resilience-plan.md → R-22.
 */
fun smbSourceConfig(
    host: String,
    share: String,
): String =
    buildJsonObject {
        put("host", host)
        put("share", share)
    }.toString()
