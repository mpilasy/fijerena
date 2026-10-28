package org.njarasoa.fijerena.core.ui.sync

import android.app.Application
import android.content.Context
import android.os.Build
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.sync.PinRequests
import org.njarasoa.fijerena.core.network.sync.SyncAccountStore
import org.njarasoa.fijerena.core.network.sync.SyncPayloads
import org.njarasoa.fijerena.core.network.sync.VolatileRecords
import java.util.UUID

/** How a PIN prompt asks another device for the PIN: [PinOnPhone] in the apps, a fake in tests. */
interface PinAsker {
    fun canAsk(context: Context): Boolean

    fun ask(
        context: Context,
        profileId: String,
    ): String?

    fun close(
        context: Context,
        requestId: String,
        profileId: String,
    )
}

/**
 * Sends PIN requests and answers through live sync — see the PIN on the phone plan and
 * [PinRequests]. Each send is pushed at once.
 */
object PinOnPhone : PinAsker {
    /** How long the asking device waits for an answer. */
    const val WAIT_MS = 2 * 60_000L

    /** This device is linked to a sync group: another device can answer. */
    override fun canAsk(context: Context): Boolean = SyncAccountStore(context).link != null

    /** Asks the other devices for [profileId]'s PIN. Returns the request id, or null when this device is not linked. */
    override fun ask(
        context: Context,
        profileId: String,
    ): String? {
        val me = SyncAccountStore(context).link?.deviceId ?: return null
        val requestId = UUID.randomUUID().toString()
        PinRequests.ask(requestId)
        send(
            context,
            me,
            SyncPayloads.PinRequest(requestId, profileId, fromDeviceName = ownName(context, me), sentAt = System.currentTimeMillis()),
        )
        return requestId
    }

    /** This device stops waiting for [requestId]: the other devices close their prompt. */
    override fun close(
        context: Context,
        requestId: String,
        profileId: String,
    ) {
        PinRequests.stopWaiting(requestId)
        val me = SyncAccountStore(context).link?.deviceId ?: return
        send(context, me, SyncPayloads.PinRequest(requestId, profileId, open = false, sentAt = System.currentTimeMillis()))
    }

    /** Answers [incoming] with [result] ([SyncPayloads.PinAnswer.UNLOCKED] or [SyncPayloads.PinAnswer.CANCELLED]). */
    fun answer(
        context: Context,
        incoming: PinRequests.Incoming,
        result: String,
    ) {
        PinRequests.done(incoming.request.requestId)
        val me = SyncAccountStore(context).link?.deviceId ?: return
        val answer = SyncPayloads.PinAnswer(incoming.request.requestId, result, fromDeviceName = ownName(context, me))
        VolatileRecords.outbox.put(PinRequests.answerKey(incoming.deviceId), SyncPayloads.encode(answer))
        push(context)
    }

    private fun send(
        context: Context,
        me: String,
        request: SyncPayloads.PinRequest,
    ) {
        VolatileRecords.outbox.put(PinRequests.requestKey(me), SyncPayloads.encode(request))
        push(context)
    }

    private fun push(context: Context) {
        SyncManager.getInstance(context.applicationContext as Application).requestSync(0)
    }

    private fun ownName(
        context: Context,
        me: String,
    ): String = AppSettings(context).deviceName(me) ?: Build.MODEL ?: ""
}
