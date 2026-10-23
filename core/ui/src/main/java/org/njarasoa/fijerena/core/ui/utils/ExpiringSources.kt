package org.njarasoa.fijerena.core.ui.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.njarasoa.fijerena.core.network.AccountManager
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.ui.di.AppContainer
import java.time.Instant
import java.time.ZoneId

/**
 * The catalogue pages' expiry banner: the profile in use's Xtream sources whose main account
 * expires in [SubscriptionExpiry.URGENT_DAYS] days or fewer, or has expired, from each one's stored
 * auth response (what the last login said). Read again whenever the profile's sources change, and on
 * each new collection (the screen opened again, the app back in front).
 */
fun expiringSources(context: Context): Flow<List<SubscriptionExpiry.ExpiringSource>> {
    val appContext = context.applicationContext
    return AppContainer
        .getInstance(appContext)
        .providerRepository
        .profileSourceSet(AppSettings(appContext).activeProfileId)
        .map { sources ->
            SubscriptionExpiry.bannerSources(
                sources.filter { it.type == "XTREAM" }.map { source ->
                    source.name to
                        SubscriptionExpiry.expiresAtSec(AccountManager(appContext, source.id).getAuthResponse()?.userInfo?.expDate)
                },
                Instant.now(),
                ZoneId.systemDefault(),
            )
        }.flowOn(Dispatchers.IO)
}
