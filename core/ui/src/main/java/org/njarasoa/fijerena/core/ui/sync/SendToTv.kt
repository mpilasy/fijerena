package org.njarasoa.fijerena.core.ui.sync

import android.app.Application
import android.content.Context
import android.os.Build
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.sync.PlayRequests
import org.njarasoa.fijerena.core.network.sync.SyncAccountStore
import org.njarasoa.fijerena.core.network.sync.SyncPayloads
import org.njarasoa.fijerena.core.network.sync.VolatileRecords

/**
 * Sends Send to TV's records through live sync — see the send to TV plan and [PlayRequests]. Each
 * send is pushed at once.
 */
object SendToTv {
    /** Asks [targetDeviceId] to play [request]; the caller waits for answers. */
    fun send(
        context: Context,
        targetDeviceId: String,
        request: SyncPayloads.PlayRequest,
    ) {
        PlayRequests.ask(request.requestId)
        VolatileRecords.outbox.put(PlayRequests.requestKey(targetDeviceId), SyncPayloads.encode(request))
        push(context)
    }

    /** The phone stops waiting: a TV that opens later shows nothing. */
    fun close(
        context: Context,
        targetDeviceId: String,
        request: SyncPayloads.PlayRequest,
    ) {
        PlayRequests.stopWaiting(request.requestId)
        VolatileRecords.outbox.put(PlayRequests.requestKey(targetDeviceId), SyncPayloads.encode(request.copy(open = false)))
        push(context)
    }

    /** This TV answers [requestId] with [result], one of the [SyncPayloads.PlayAnswer] codes. */
    fun answer(
        context: Context,
        requestId: String,
        result: String,
    ) {
        val me = SyncAccountStore(context).link?.deviceId ?: return
        val answer = SyncPayloads.PlayAnswer(requestId, result, fromDeviceName = ownName(context, me))
        VolatileRecords.outbox.put(PlayRequests.answerKey(me), SyncPayloads.encode(answer))
        push(context)
    }

    private fun push(context: Context) {
        SyncManager.getInstance(context.applicationContext as Application).requestSync(0)
    }

    /** This device's name as the group calls it. */
    fun ownName(
        context: Context,
        me: String,
    ): String = AppSettings(context).deviceName(me) ?: Build.MODEL ?: ""
}
