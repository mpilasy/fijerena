package org.njarasoa.fijerena.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.profile.ProfilePins
import org.njarasoa.fijerena.core.network.profile.ProfileRepository
import org.njarasoa.fijerena.core.network.sync.PinRequests
import org.njarasoa.fijerena.core.network.sync.SyncPayloads
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.PinRequest
import org.njarasoa.fijerena.core.ui.components.rememberPinPromptState
import org.njarasoa.fijerena.core.ui.sync.PinOnPhone

/**
 * Another device's PIN request, over any screen (the PIN on the phone plan). This phone checks
 * the PIN against the synced hash and answers unlocked, never the PIN; Cancel answers cancelled.
 * It closes by itself when the other device stops waiting, and after [PinOnPhone.WAIT_MS] with no
 * word from it. A right PIN unlocks nothing on this phone.
 */
@Composable
fun PinRequestHost() {
    val context = LocalContext.current
    val incoming by PinRequests.incoming.collectAsStateWithLifecycle()
    val request = incoming.values.minByOrNull { it.receivedAt } ?: return
    val profiles by remember { ProfileRepository(context).observeProfiles() }.collectAsStateWithLifecycle(emptyList())
    val pins = remember { ProfilePins(AppSettings(context)) }
    val profileName =
        profiles.firstOrNull { it.id == request.request.profileId }?.name
            ?: request.request.profileName.ifBlank { return }
    val requestId = request.request.requestId
    LaunchedEffect(requestId) {
        // The asking device closes it when it stops waiting; this is the backstop for one that went away.
        delay(PinOnPhone.WAIT_MS)
        PinRequests.done(requestId)
    }
    val state =
        rememberPinPromptState(
            pins,
            PinRequest.Check(request.request.profileId, stringResource(R.string.pin_enter_for, profileName), unlockHere = false),
        ) {
            PinOnPhone.answer(context, request, SyncPayloads.PinAnswer.UNLOCKED)
        }
    PinDialog(
        state = state,
        onCancel = { PinOnPhone.answer(context, request, SyncPayloads.PinAnswer.CANCELLED) },
        note =
            request.request.fromDeviceName
                .takeIf { it.isNotBlank() }
                ?.let { stringResource(R.string.pin_asked_by, it) },
    )
}
