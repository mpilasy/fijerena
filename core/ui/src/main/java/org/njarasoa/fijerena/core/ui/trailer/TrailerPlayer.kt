package org.njarasoa.fijerena.core.ui.trailer

import android.content.res.Configuration
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.utils.YouTubePlayerTracker
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.theme.CinemaIcons
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.utils.openExternalUrl
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder

/**
 * The YouTube video id in a trailer link, or null when the link is not a YouTube video. Takes the
 * watch, short-link, embed and shorts forms.
 */
fun youTubeVideoId(url: String): String? {
    val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
    val host =
        uri.host
            ?.lowercase()
            ?.removePrefix("www.")
            ?.removePrefix("m.") ?: return null
    val segments =
        uri.path
            .orEmpty()
            .split('/')
            .filter { it.isNotEmpty() }
    val id =
        when (host) {
            "youtu.be" -> {
                segments.firstOrNull()
            }

            "youtube.com", "youtube-nocookie.com" -> {
                when (segments.firstOrNull()) {
                    "watch" -> {
                        uri.rawQuery
                            ?.split('&')
                            ?.firstOrNull { it.startsWith("v=") }
                            ?.removePrefix("v=")
                    }

                    "embed", "shorts", "v", "live" -> {
                        segments.getOrNull(1)
                    }

                    else -> {
                        null
                    }
                }
            }

            else -> {
                null
            }
        }
    return id?.takeIf { VIDEO_ID.matches(it) }
}

private val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")

private const val SEEK_SECONDS = 10f
private const val SEEK_LABEL_MS = 2000L
private const val SECONDS_PER_MINUTE = 60
private const val TITLE_SECONDS = 5f
private const val OEMBED_TIMEOUT_MS = 5000

/** The `title` in a YouTube oEmbed answer, or null. */
internal fun oEmbedTitle(json: String): String? =
    runCatching {
        Json
            .parseToJsonElement(json)
            .jsonObject["title"]
            ?.jsonPrimitive
            ?.content
    }.getOrNull()?.takeIf { it.isNotBlank() }

/**
 * The video's title from YouTube's oEmbed address (public, no key), or null when the call fails.
 * The embedded player gives no title of its own to the app.
 */
private suspend fun fetchYouTubeTitle(videoId: String): String? =
    withContext(Dispatchers.IO) {
        runCatching {
            val watch = URLEncoder.encode("https://www.youtube.com/watch?v=$videoId", "UTF-8")
            val connection = URL("https://www.youtube.com/oembed?format=json&url=$watch").openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = OEMBED_TIMEOUT_MS
                connection.readTimeout = OEMBED_TIMEOUT_MS
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
                oEmbedTitle(connection.inputStream.bufferedReader().use { it.readText() })
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

/**
 * What the Trailer button calls. A YouTube trailer plays in the app over the current screen; any
 * other link opens in whatever app claims it. [fallbackUrl] (TMDB's trailer) plays when the first
 * one fails.
 */
class TrailerLauncher internal constructor(
    private val play: (url: String, fallbackUrl: String?) -> Unit,
) {
    operator fun invoke(
        url: String,
        fallbackUrl: String? = null,
    ) = play(url, fallbackUrl)
}

/** Set by [TrailerHost]; null outside it, where a trailer opens in another app. */
private val LocalTrailerLauncher = staticCompositionLocalOf<TrailerLauncher?> { null }

@Composable
fun rememberPlayTrailer(): TrailerLauncher {
    val provided = LocalTrailerLauncher.current
    val context = LocalContext.current
    return provided ?: remember(context) { TrailerLauncher { url, _ -> openExternalUrl(context, url) } }
}

private data class TrailerRequest(
    val videoId: String,
    val url: String,
    val fallbackUrl: String?,
)

/** The trailer to play first: [url] when it is a YouTube video, else [fallbackUrl] when that is. */
private fun firstPlayable(
    url: String,
    fallbackUrl: String?,
): TrailerRequest? {
    youTubeVideoId(url)?.let { return TrailerRequest(it, url, fallbackUrl) }
    val fallback = fallbackUrl ?: return null
    return youTubeVideoId(fallback)?.let { TrailerRequest(it, fallback, null) }
}

/** YouTube errors that mean the video is gone (removed, private, a bad id), not only kept out of other apps. */
private val VIDEO_GONE =
    setOf(PlayerConstants.PlayerError.VIDEO_NOT_FOUND, PlayerConstants.PlayerError.INVALID_PARAMETER_IN_REQUEST)

/**
 * Hosts the in-app trailer player for the whole app. When a trailer fails, its fallback plays
 * instead. With none left, a video that is gone shows a message, and any other failure (a video
 * its owner keeps out of other apps) opens the video in the YouTube app.
 */
@Composable
fun TrailerHost(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val unavailable = stringResource(R.string.trailer_unavailable)
    var request by remember { mutableStateOf<TrailerRequest?>(null) }
    val launcher =
        remember(context) {
            TrailerLauncher { url, fallbackUrl ->
                val first = firstPlayable(url, fallbackUrl)
                if (first != null) request = first else openExternalUrl(context, url)
            }
        }
    CompositionLocalProvider(LocalTrailerLauncher provides launcher) { content() }
    request?.let { current ->
        key(current.videoId) {
            TrailerDialog(
                videoId = current.videoId,
                onClose = { request = null },
                onError = { error ->
                    val fallback =
                        current.fallbackUrl
                            ?.let(::youTubeVideoId)
                            ?.takeIf { it != current.videoId }
                    request = fallback?.let { TrailerRequest(it, current.fallbackUrl, null) }
                    when {
                        fallback != null -> {
                            Unit
                        }

                        error in VIDEO_GONE -> {
                            Toast.makeText(context, unavailable, Toast.LENGTH_LONG).show()
                        }

                        else -> {
                            openExternalUrl(context, current.url)
                        }
                    }
                },
            )
        }
    }
}

/** "1:05": [seconds] as minutes and seconds. */
internal fun trailerTime(seconds: Float): String {
    val whole = seconds.toInt().coerceAtLeast(0)
    return "%d:%02d".format(whole / SECONDS_PER_MINUTE, whole % SECONDS_PER_MINUTE)
}

/**
 * The trailer, full screen. The player view never takes focus, so a TV remote drives it through
 * this screen: OK or play/pause toggles, left and right (or rewind and fast-forward) seek 10
 * seconds, Back closes. A pause from the remote shows a pause sign until play resumes, and a seek
 * shows the new position for two seconds. On TV the video's title shows for five seconds of play
 * once it is known, and while paused from the remote. On a phone the player's own controls (with
 * the title) work by touch.
 */
@Composable
private fun TrailerDialog(
    videoId: String,
    onClose: () -> Unit,
    onError: (PlayerConstants.PlayerError) -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val tracker = remember { YouTubePlayerTracker() }
    var player by remember { mutableStateOf<YouTubePlayer?>(null) }
    var pausedByKey by remember { mutableStateOf(false) }
    var seekLabel by remember { mutableStateOf<String?>(null) }
    var seekCount by remember { mutableIntStateOf(0) }
    var position by remember { mutableFloatStateOf(0f) }

    // The title shows for five seconds of play from when it arrives, however late the call answers.
    var titleUntil by remember { mutableFloatStateOf(TITLE_SECONDS) }
    // A phone shows YouTube's own title with its controls, a tap away; a TV remote never brings them up.
    val isTv =
        LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
    val title by produceState<String?>(null, videoId, isTv) {
        value = if (isTv) fetchYouTubeTitle(videoId) else null
        titleUntil = position + TITLE_SECONDS
    }

    val currentOnClose by rememberUpdatedState(onClose)
    val currentOnError by rememberUpdatedState(onError)

    LaunchedEffect(seekCount) {
        if (seekLabel != null) {
            delay(SEEK_LABEL_MS)
            seekLabel = null
        }
    }

    fun seekBy(
        youTubePlayer: YouTubePlayer,
        seconds: Float,
    ) {
        val duration = tracker.videoDuration
        val target = (tracker.currentSecond + seconds).coerceIn(0f, if (duration > 0f) duration else Float.MAX_VALUE)
        youTubePlayer.seekTo(target)
        seekLabel = if (duration > 0f) "${trailerTime(target)} / ${trailerTime(duration)}" else trailerTime(target)
        seekCount++
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black) // look-ok: video letterbox, black on every theme
                    .focusRequester(focus)
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        val youTubePlayer = player
                        if (event.type != KeyEventType.KeyDown || youTubePlayer == null) return@onPreviewKeyEvent false
                        when (event.nativeKeyEvent.keyCode) {
                            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE,
                            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                            -> {
                                if (tracker.state == PlayerConstants.PlayerState.PLAYING) {
                                    youTubePlayer.pause()
                                    pausedByKey = true
                                } else {
                                    youTubePlayer.play()
                                }
                            }

                            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                                youTubePlayer.play()
                            }

                            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                                youTubePlayer.pause()
                                pausedByKey = true
                            }

                            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                                seekBy(youTubePlayer, SEEK_SECONDS)
                            }

                            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                                seekBy(youTubePlayer, -SEEK_SECONDS)
                            }

                            else -> {
                                return@onPreviewKeyEvent false
                            }
                        }
                        true
                    },
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { context ->
                    YouTubePlayerView(context).apply {
                        enableAutomaticInitialization = false
                        isFocusable = false
                        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                        lifecycle.addObserver(this)
                        val listener =
                            object : AbstractYouTubePlayerListener() {
                                override fun onReady(youTubePlayer: YouTubePlayer) {
                                    youTubePlayer.addListener(tracker)
                                    player = youTubePlayer
                                    youTubePlayer.loadVideo(videoId, 0f)
                                }

                                override fun onStateChange(
                                    youTubePlayer: YouTubePlayer,
                                    state: PlayerConstants.PlayerState,
                                ) {
                                    if (state == PlayerConstants.PlayerState.PLAYING) pausedByKey = false
                                    if (state == PlayerConstants.PlayerState.ENDED) currentOnClose()
                                }

                                override fun onCurrentSecond(
                                    youTubePlayer: YouTubePlayer,
                                    second: Float,
                                ) {
                                    position = second
                                }

                                override fun onError(
                                    youTubePlayer: YouTubePlayer,
                                    error: PlayerConstants.PlayerError,
                                ) {
                                    currentOnError(error)
                                }
                            }
                        val options =
                            IFramePlayerOptions
                                .Builder(context)
                                .controls(1)
                                .rel(0)
                                .ivLoadPolicy(3)
                                .fullscreen(0)
                                .build()
                        initialize(listener, true, options)
                    }
                },
                onRelease = { view ->
                    lifecycle.removeObserver(view)
                    view.release()
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (pausedByKey) {
                Icon(
                    imageVector = CinemaIcons.Pause,
                    contentDescription = null,
                    tint = CinemaTheme.colors.onMedia,
                    modifier =
                        Modifier
                            .background(CinemaTheme.colors.mediaScrim, CinemaTheme.shapes.chip)
                            .padding(CinemaSpacing.md)
                            .size(CinemaSpacing.xxl),
                )
            }
            title?.takeIf { position < titleUntil || pausedByKey }?.let { name ->
                CinemaText(
                    text = name,
                    role = TextRole.Title,
                    tone = TextTone.OnMedia,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    keepCase = true,
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(CinemaSpacing.xl)
                            .background(CinemaTheme.colors.mediaScrim, CinemaTheme.shapes.chip)
                            .padding(horizontal = CinemaSpacing.md, vertical = CinemaSpacing.xs),
                )
            }
            seekLabel?.let { label ->
                CinemaText(
                    text = label,
                    role = TextRole.Title,
                    tone = TextTone.OnMedia,
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = CinemaSpacing.xxl)
                            .background(CinemaTheme.colors.mediaScrim, CinemaTheme.shapes.chip)
                            .padding(horizontal = CinemaSpacing.md, vertical = CinemaSpacing.xs),
                )
            }
        }
    }
}
