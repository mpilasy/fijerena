package org.njarasoa.fijerena.core.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.njarasoa.fijerena.core.network.provider.ProviderEntity
import org.njarasoa.fijerena.core.network.provider.ProviderRepository
import org.njarasoa.fijerena.core.network.provider.RefusedLogins

/** The source for each app's refused password pop-up ([RefusedLogins]), or null. */
@Composable
fun refusedLoginSource(): ProviderEntity? {
    val context = LocalContext.current
    val id by RefusedLogins.first.collectAsStateWithLifecycle(initialValue = null)
    val source by produceState<ProviderEntity?>(null, id) {
        value =
            id?.let { refused ->
                // A source deleted since: nothing to show.
                ProviderRepository(context.applicationContext).getProviderById(refused).also {
                    if (it ==
                        null
                    ) {
                        RefusedLogins.dismiss(refused)
                    }
                }
            }
    }
    return source?.takeIf { it.id == id }
}
