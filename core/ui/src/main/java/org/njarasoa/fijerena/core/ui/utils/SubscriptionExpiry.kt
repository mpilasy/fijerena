package org.njarasoa.fijerena.core.ui.utils

import android.content.res.Resources
import org.njarasoa.fijerena.core.ui.R
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * How close an Xtream subscription's end is: the colour of a login's expiry in Edit Source and the
 * catalogue pages' banner. Days left are whole calendar days from today in [ZoneId] (the device's
 * time zone): 0 is today, negative once past.
 */
object SubscriptionExpiry {
    /** At or under this many days left the expiry shows in the warning colour. */
    const val SOON_DAYS = 15L

    /** At or under this many days left (or once past) it shows in the error colour, and the banner shows. */
    const val URGENT_DAYS = 7L

    enum class Level { NORMAL, SOON, URGENT }

    /** A source the catalogue banner names: [daysLeft] is at most [URGENT_DAYS], negative once expired. */
    data class ExpiringSource(
        val name: String,
        val daysLeft: Long,
    )

    /** The panel's `exp_date` (Unix seconds) as a time, null when unlimited or missing ("Unlimited", "0", null). */
    fun expiresAtSec(expDate: String?): Long? = expDate?.toLongOrNull()?.takeIf { it > 0 }

    fun daysLeft(
        expiresAtSec: Long,
        now: Instant,
        zone: ZoneId,
    ): Long =
        ChronoUnit.DAYS.between(
            now.atZone(zone).toLocalDate(),
            Instant.ofEpochSecond(expiresAtSec).atZone(zone).toLocalDate(),
        )

    /** [daysLeft] null: no end (unlimited or unknown). */
    fun level(daysLeft: Long?): Level =
        when {
            daysLeft == null -> Level.NORMAL
            daysLeft <= URGENT_DAYS -> Level.URGENT
            daysLeft <= SOON_DAYS -> Level.SOON
            else -> Level.NORMAL
        }

    /** [expiresAtSec] null: no end. */
    fun level(
        expiresAtSec: Long?,
        now: Instant,
        zone: ZoneId,
    ): Level = level(expiresAtSec?.let { daysLeft(it, now, zone) })

    /**
     * The sources the banner names, in the order given: those expiring in [URGENT_DAYS] or fewer,
     * or already expired. [sources]: each source's name and its expiry (null: no end).
     */
    fun bannerSources(
        sources: List<Pair<String, Long?>>,
        now: Instant,
        zone: ZoneId,
    ): List<ExpiringSource> =
        sources.mapNotNull { (name, expiresAtSec) ->
            expiresAtSec
                ?.let { daysLeft(it, now, zone) }
                ?.takeIf { it <= URGENT_DAYS }
                ?.let { ExpiringSource(name, it) }
        }

    /** "panel-d expires today / tomorrow / in 5 days", "panel-d expired 2 days ago". */
    fun bannerText(
        resources: Resources,
        source: ExpiringSource,
    ): String {
        val days = source.daysLeft
        return when {
            days < 0 -> resources.getQuantityString(R.plurals.source_expiry_expired_days_ago, (-days).toInt(), source.name, -days)
            days == 0L -> resources.getString(R.string.source_expiry_today, source.name)
            days == 1L -> resources.getString(R.string.source_expiry_tomorrow, source.name)
            else -> resources.getQuantityString(R.plurals.source_expiry_in_days, days.toInt(), source.name, days)
        }
    }
}
