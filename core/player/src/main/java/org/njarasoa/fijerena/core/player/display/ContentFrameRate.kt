package org.njarasoa.fijerena.core.player.display

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The frame rate of the content that plays (the match frame rate plan, Phase 1): for VOD, the
 * rate in the video format when the container sets it; for live, or VOD without a rate, the rate
 * that [FrameRateEstimator] finds in the frames. Null while it is not known.
 *
 * The frames come on the playback thread and a new stream or a seek on the main thread, so each
 * call locks.
 */
class ContentFrameRate {
    private val estimator = FrameRateEstimator()
    private var live = false
    private val _rate = MutableStateFlow<Float?>(null)
    val rate: StateFlow<Float?> = _rate.asStateFlow()

    /** A new stream starts: forget the frames of the one before. */
    @Synchronized
    fun newStream(isLive: Boolean) {
        live = isLive
        estimator.reset()
        _rate.value = null
    }

    /** A seek: the steps across it are not frame steps. */
    @Synchronized
    fun onSeek() {
        estimator.reset()
        _rate.value = null
    }

    /** A frame is about to show at [presentationTimeUs]; [formatFrameRate] is its format's rate, or not set. */
    @Synchronized
    fun onFrame(
        presentationTimeUs: Long,
        formatFrameRate: Float,
    ) {
        val estimated = estimator.onFrame(presentationTimeUs)
        _rate.value = formatFrameRate.takeIf { !live && it > 0f } ?: estimated
    }
}
