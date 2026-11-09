package org.njarasoa.fijerena.core.ui.sync

import android.app.Application
import androidx.annotation.StringRes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import org.njarasoa.fijerena.core.network.AppSettings
import org.njarasoa.fijerena.core.network.provider.SettingsDatabase
import org.njarasoa.fijerena.core.network.sync.DeviceBuild
import org.njarasoa.fijerena.core.network.sync.PlayRequests
import org.njarasoa.fijerena.core.network.sync.SyncAccountManager
import org.njarasoa.fijerena.core.network.sync.SyncAccountStore
import org.njarasoa.fijerena.core.network.sync.SyncPayloads
import org.njarasoa.fijerena.core.network.sync.SyncPayloads.PlayAnswer
import org.njarasoa.fijerena.core.player.domain.ContentType
import org.njarasoa.fijerena.core.player.domain.isResumableAt
import org.njarasoa.fijerena.core.ui.di.AppContainer
import org.njarasoa.fijerena.core.ui.utils.AppNotices
import org.njarasoa.fijerena.core.ui.viewmodels.SendItem
import org.njarasoa.fijerena.core.ui.viewmodels.SendToTvGateway
import org.njarasoa.fijerena.core.ui.viewmodels.SendToTvViewModel
import org.njarasoa.fijerena.core.ui.viewmodels.TvRow
import org.njarasoa.fijerena.core.ui.viewmodels.tvRows

/** The real [SendToTvGateway]: live sync, the phone's own sources, the app's notices. */
class SendToTvAppGateway(
    private val app: Application,
) : SendToTvGateway {
    private val settings = AppSettings(app)
    private val store = SyncAccountStore(app)

    /** Linked, and a TV of this group reports the feature (builds of devices can outlive the link). */
    override val available: Flow<Boolean> =
        combine(settings.observeDeviceBuilds(), SyncManager.getInstance(app).status) { builds, _ ->
            store.link != null && builds.values.any { it.app == "tv" && it.takesPlayRequests }
        }

    override val answers: SharedFlow<PlayAnswer> = PlayRequests.answers

    override suspend fun rows(): List<TvRow> {
        val devices = SyncAccountManager(app).devices()
        val builds: Map<String, DeviceBuild> = settings.deviceBuilds()
        val now = System.currentTimeMillis()
        val names = settings.observeDeviceNames().first()
        return tvRows(devices, builds, names, now) { SendToTvViewModel.playingTitle(it, now) }
    }

    override suspend fun buildRequest(
        item: SendItem,
        position: Long?,
    ): SyncPayloads.PlayRequest? {
        val db = SettingsDatabase.getInstance(app)
        val providerKey = db.settingsSyncDao().providerKey(item.providerId) ?: return null
        val me = store.link?.deviceId ?: return null
        val repository = AppContainer.getInstance(app).getMediaRepository(item.providerId)
        val vod = item.contentType != ContentType.LIVE_TV
        val saved =
            if (position == null && vod) {
                repository.getPlaybackPositionSuspend(item.streamId, item.contentType)
            } else {
                null
            }
        val sentPosition =
            position?.takeIf { vod && it > 0 }
                ?: saved?.takeIf { isResumableAt(it.playbackPosition, it.duration) && !it.isCompleted }?.playbackPosition
        // A list row does not know a film's file type; the phone asks its source once, as the
        // details page does.
        val extension =
            item.episodeExtension
                ?: if (item.contentType == ContentType.MOVIES) repository.getMovieDetail(item.streamId).getOrNull()?.extension else null
        val profileId = settings.activeProfileId
        val profileName =
            db
                .profileDao()
                .getAll()
                .firstOrNull { it.id == profileId }
                ?.name
                .orEmpty()
        return SyncPayloads.PlayRequest(
            requestId = SendToTvViewModel.newRequestId(),
            profileId = profileId,
            profileName = profileName,
            fromDeviceName = SendToTv.ownName(app, me),
            providerKey = providerKey,
            streamId = item.streamId,
            streamName = item.streamName,
            categoryId = item.categoryId,
            contentType = item.contentType,
            episodeId = item.episodeId,
            episodeExtension = extension,
            seriesId = item.seriesId,
            seriesName = item.seriesName,
            positionMs = sentPosition,
            title = item.title,
            subtitle = item.subtitle,
            sentAt = System.currentTimeMillis(),
        )
    }

    override fun send(
        targetDeviceId: String,
        request: SyncPayloads.PlayRequest,
    ) = SendToTv.send(app, targetDeviceId, request)

    override fun close(
        targetDeviceId: String,
        request: SyncPayloads.PlayRequest,
    ) = SendToTv.close(app, targetDeviceId, request)

    override fun stopWaiting(requestId: String) = PlayRequests.stopWaiting(requestId)

    override fun notify(
        @StringRes text: Int,
        vararg args: Any,
    ) = AppNotices.show(app.getString(text, *args))
}
