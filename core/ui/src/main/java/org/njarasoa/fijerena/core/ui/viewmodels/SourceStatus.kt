package org.njarasoa.fijerena.core.ui.viewmodels

import android.content.Context
import org.njarasoa.fijerena.core.network.AccountManager
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.player.domain.ProviderType
import org.njarasoa.fijerena.core.ui.R

/**
 * One source as the Sources list describes it under its name: its kind, and the subscription facts
 * an Xtream source reports at login (null / false when it reports none); [inProfile] whether the
 * profile in use has it.
 */
data class SourceStatus(
    val type: String,
    val expiry: String? = null,
    val isExpired: Boolean = false,
    val maxConnections: String? = null,
    val isTrial: Boolean = false,
    val inProfile: Boolean = true,
)

/** [entity]'s [SourceStatus], read from the login answer this device keeps (no network call). */
fun sourceStatus(
    context: Context,
    entity: ProviderEntity,
    inProfile: Boolean,
): SourceStatus {
    val type = runCatching { ProviderType.valueOf(entity.type) }.getOrNull()
    val info =
        if (type == ProviderType.XTREAM) AccountManager(context.applicationContext, entity.id).getAuthResponse()?.userInfo else null
    return SourceStatus(
        type = type?.displayName ?: entity.type,
        expiry = formatExpiryDate(context, info?.expDate),
        isExpired = info?.status?.equals("Expired", ignoreCase = true) == true,
        maxConnections = info?.maxConnections,
        isTrial = info?.isTrial == "1",
        inProfile = inProfile,
    )
}

private fun formatExpiryDate(
    context: Context,
    expDate: String?,
): String? =
    when {
        expDate.isNullOrEmpty() -> {
            null
        }

        expDate.equals("Unlimited", ignoreCase = true) -> {
            context.getString(R.string.subscription_unlimited)
        }

        else -> {
            expDate.toLongOrNull()?.let { epoch ->
                java.time.Instant
                    .ofEpochSecond(epoch)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate()
                    .format(
                        java.time.format.DateTimeFormatter
                            .ofPattern("MMM d, yyyy"),
                    )
            } ?: expDate
        }
    }
