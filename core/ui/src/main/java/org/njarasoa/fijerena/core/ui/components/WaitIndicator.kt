package org.njarasoa.fijerena.core.ui.components

import android.graphics.Bitmap
import androidx.annotation.OptIn
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.njarasoa.fijerena.core.navigation.ContentType
import org.njarasoa.fijerena.core.player.domain.parseDisplayTitle
import org.njarasoa.fijerena.core.player.model.BufferingInfo
import org.njarasoa.fijerena.core.player.model.PlayerMetadata
import org.njarasoa.fijerena.core.player.service.StreamingPlaybackService
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.theme.CinemaAlpha
import org.njarasoa.fijerena.core.ui.theme.CinemaSpacing
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.theme.WaitStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** What the player waits for decides the picture: an iris for a film, a reel for an episode, a ring for a channel. */
enum class WaitKind { Film, Episode, Live }

/** Catch-up is a channel's archive: it waits as a channel does. */
fun waitKindOf(metadata: PlayerMetadata): WaitKind =
    when {
        metadata.isLive || metadata.catchupLabel != null -> WaitKind.Live
        metadata.showTitle != null -> WaitKind.Episode
        else -> WaitKind.Film
    }

/** Before the stream is known: from the route's content type. */
fun waitKindOf(
    contentType: String,
    isCatchup: Boolean = false,
): WaitKind =
    when {
        contentType == ContentType.LIVE_TV.name || isCatchup -> WaitKind.Live
        contentType == ContentType.TV_SHOWS.name -> WaitKind.Episode
        else -> WaitKind.Film
    }

/** The name the badge and the ring show when there is no logo: the clean channel, show or film name. */
fun waitName(metadata: PlayerMetadata): String =
    when (waitKindOf(metadata)) {
        WaitKind.Live -> metadata.channelName.ifBlank { metadata.title }.let { parseDisplayTitle(it).title.ifBlank { it } }
        WaitKind.Episode -> metadata.showTitle.orEmpty()
        WaitKind.Film -> parseDisplayTitle(metadata.title).title.ifBlank { metadata.title }
    }

/**
 * The player's wait, without words: [kind]'s picture over a soft disc, filled by how much video is
 * held of what playback waits for, and in the warning colour while the connection is slower than
 * the stream. [tint] is the colour taken from the frozen frame ([rememberSceneTint]); the theme's
 * accent without one. A [WaitKind.Live] ring shows [logoUrl], or [name] when there is none.
 */
@Composable
fun WaitIndicator(
    kind: WaitKind,
    modifier: Modifier = Modifier,
    size: Dp = WaitStyle.indicatorSize,
    logoUrl: String? = null,
    name: String = "",
    tint: Color? = null,
    contentDescription: String = stringResource(R.string.loading_buffering),
) {
    val info = rememberBufferingInfo()
    val c = CinemaTheme.colors
    val color = if (info?.tooSlow == true) c.warning else tint ?: c.accent
    val progress = WaitMath.progress(info)
    Box(
        modifier =
            modifier
                .size(size * WaitStyle.DISC_SCALE)
                .background(Brush.radialGradient(listOf(c.mediaScrim.copy(alpha = CinemaAlpha.scrim), c.clear)))
                .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        when (kind) {
            WaitKind.Film -> Iris(progress, color, c.mediaScrim, Modifier.size(size))
            WaitKind.Episode -> Reel(WaitMath.reelTurnsPerSecond(info), color, Modifier.size(size))
            WaitKind.Live -> Ring(progress, color, logoUrl, name, size)
        }
    }
}

/**
 * The player's wait over the full screen: [WaitIndicator] in the centre, the title or channel logo
 * (or its name) at the top left unless [showBadge] is false (the TV banner shows it then), and
 * Expert mode's [BufferingDetails] under the indicator when [showDetails]. The indicator is always
 * at the centre of the screen; the details hang below it.
 */
@Composable
fun PlayerWait(
    metadata: PlayerMetadata,
    modifier: Modifier = Modifier,
    kind: WaitKind = waitKindOf(metadata),
    tint: Color? = null,
    showBadge: Boolean = true,
    showDetails: Boolean = false,
    badgeLogoHeight: Dp = WaitStyle.badgeLogoHeight,
    badgeModifier: Modifier = Modifier,
    size: Dp = WaitStyle.indicatorSize,
) {
    val name = waitName(metadata)
    Box(modifier = modifier.fillMaxSize()) {
        if (showBadge && name.isNotBlank()) {
            StreamBadge(
                logoUrl = metadata.logoUrl,
                name = name,
                logoHeight = badgeLogoHeight,
                modifier = badgeModifier.align(Alignment.TopStart),
            )
        }
        WaitIndicator(
            kind = kind,
            modifier = Modifier.align(Alignment.Center),
            size = size,
            logoUrl = metadata.logoUrl,
            name = name,
            tint = tint,
        )
        // Below the indicator, so the details never push it off the centre. A soft backing: they may
        // sit on a light frozen frame.
        if (showDetails) {
            Box(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxHeight(HALF).padding(top = size / 2 + CinemaSpacing.sm),
                contentAlignment = Alignment.TopCenter,
            ) {
                BufferingDetails(
                    modifier =
                        Modifier
                            .background(CinemaTheme.colors.mediaScrim.copy(alpha = CinemaAlpha.scrim), CinemaTheme.shapes.chip)
                            .padding(horizontal = CinemaSpacing.md, vertical = CinemaSpacing.xs),
                )
            }
        }
    }
}

/** What is loading, at the top left: its logo, or its name when it has none or the logo fails. */
@Composable
fun StreamBadge(
    logoUrl: String?,
    name: String,
    modifier: Modifier = Modifier,
    logoHeight: Dp = WaitStyle.badgeLogoHeight,
) {
    TitleLogoOrText(contentDescription = name, logoUrl = logoUrl, modifier = modifier, logoHeight = logoHeight) {
        CinemaText(text = name, role = TextRole.Title, tone = TextTone.OnMedia, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * A colour from the frame on screen while [active], for the wait indicator over that frame: the
 * scene's main hue, light on a dark frame and dark on a light one. Null (the accent) before the
 * first capture, when [capture] gives no frame, or on a grey frame.
 */
@Composable
fun rememberSceneTint(
    active: Boolean,
    capture: suspend () -> Bitmap?,
): Color? {
    var tint by remember { mutableStateOf<Color?>(null) }
    val currentCapture by rememberUpdatedState(capture)
    LaunchedEffect(active) {
        tint = null
        if (!active) return@LaunchedEffect
        val frame = currentCapture() ?: return@LaunchedEffect
        tint =
            withContext(Dispatchers.Default) {
                val pixels = IntArray(frame.width * frame.height)
                frame.getPixels(pixels, 0, frame.width, 0, 0, frame.width, frame.height)
                WaitMath.sceneTint(pixels, frame.width)?.let(::Color)
            }
    }
    return tint
}

/** The playback service's [BufferingInfo], read twice a second (as [BufferingDetails] does). */
@OptIn(UnstableApi::class)
@Composable
private fun rememberBufferingInfo(): BufferingInfo? {
    var info by remember { mutableStateOf<BufferingInfo?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            info = StreamingPlaybackService.getInstance()?.bufferingInfo()
            delay(POLL_MS)
        }
    }
    return info
}

/**
 * A camera iris: six blades turn and open as the buffer fills; fully open, the film starts. Until
 * the player knows how much it needs, it opens and closes a little.
 */
@Composable
private fun Iris(
    progress: Float?,
    color: Color,
    seamColor: Color,
    modifier: Modifier,
) {
    val motion = rememberInfiniteTransition(label = "iris")
    val turn by motion.animateFloat(
        initialValue = 0f,
        targetValue = FULL_TURN,
        animationSpec = infiniteRepeatable(tween(WaitStyle.IRIS_TURN_MS, easing = LinearEasing)),
        label = "iris turn",
    )
    val breath by motion.animateFloat(
        initialValue = IRIS_BREATH_MIN,
        targetValue = IRIS_BREATH_MAX,
        animationSpec = infiniteRepeatable(tween(WaitStyle.IRIS_BREATH_MS), RepeatMode.Reverse),
        label = "iris breath",
    )
    val filled by animateFloatAsState(progress ?: 0f, label = "iris fill")
    val opening = if (progress == null) breath else WaitMath.irisOpening(filled)
    Canvas(modifier) {
        val stroke = WaitStyle.stroke.toPx()
        val outer = size.minDimension / 2 - stroke
        val blades = outer * IRIS_BLADE_SCALE
        // The blades turn a sixth of a turn as they open, as a real iris does.
        val angle = turn + filled * BLADE_TURN
        val hole = polygon(center, blades * opening, IRIS_BLADES, angle)
        val disc =
            Path().apply {
                fillType = PathFillType.EvenOdd
                addOval(Rect(center, blades))
                hole.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
                close()
            }
        drawPath(disc, color.copy(alpha = CinemaAlpha.textHigh))
        // A seam from each corner of the opening to the rim: where one blade lies on the next.
        hole.forEachIndexed { i, from ->
            val to = hole[(i + 1) % hole.size]
            val rim = WaitMath.toCircle(from = to - center, direction = to - from, radius = blades)
            drawLine(seamColor, to, center + rim, strokeWidth = WaitStyle.seam.toPx())
        }
        drawCircle(color, radius = outer, style = Stroke(stroke))
    }
}

/**
 * A film reel turning at the speed the data arrives: quickly on a good connection, slowly when it
 * crawls, nearly still when nothing comes.
 */
@Composable
private fun Reel(
    turnsPerSecond: Float,
    color: Color,
    modifier: Modifier,
) {
    val speed by animateFloatAsState(turnsPerSecond, label = "reel speed")
    val currentSpeed by rememberUpdatedState(speed)
    var angle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                angle = (angle + currentSpeed * FULL_TURN * (now - last) / NANOS_PER_SECOND) % FULL_TURN
                last = now
            }
        }
    }
    Canvas(modifier) {
        val stroke = WaitStyle.stroke.toPx()
        val outer = size.minDimension / 2 - stroke
        val reel = outer * REEL_SCALE
        rotate(angle) {
            val body =
                Path().apply {
                    fillType = PathFillType.EvenOdd
                    addOval(Rect(center, reel))
                    for (i in 0 until REEL_HOLES) {
                        val a = (i * FULL_TURN / REEL_HOLES) * DEG_TO_RAD
                        val hole = center + Offset(cos(a), sin(a)) * (reel * REEL_HOLE_DISTANCE)
                        addOval(Rect(hole, reel * REEL_HOLE_SIZE))
                    }
                    addOval(Rect(center, reel * REEL_HUB_SIZE))
                }
            drawPath(body, color.copy(alpha = CinemaAlpha.textHigh))
        }
        drawCircle(color, radius = outer, style = Stroke(stroke))
    }
}

/** A ring that fills as the buffer fills, around the channel's logo or name; a turning arc until the need is known. */
@Composable
private fun Ring(
    progress: Float?,
    color: Color,
    logoUrl: String?,
    name: String,
    size: Dp,
) {
    val turn by rememberInfiniteTransition(label = "ring").animateFloat(
        initialValue = 0f,
        targetValue = FULL_TURN,
        animationSpec = infiniteRepeatable(tween(WaitStyle.RING_TURN_MS, easing = LinearEasing)),
        label = "ring turn",
    )
    val filled by animateFloatAsState(progress ?: 0f, label = "ring fill")
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = WaitStyle.stroke.toPx()
            drawCircle(color.copy(alpha = CinemaAlpha.tint), radius = this.size.minDimension / 2 - stroke, style = Stroke(stroke))
            if (progress == null) {
                arc(color, start = turn - QUARTER_TURN, sweep = QUARTER_TURN, stroke = stroke)
            } else {
                arc(color, start = -QUARTER_TURN, sweep = filled * FULL_TURN, stroke = stroke)
            }
        }
        var logoFailed by remember(logoUrl) { mutableStateOf(false) }
        val inner = Modifier.size(size * WaitStyle.RING_CONTENT_SCALE)
        if (logoUrl != null && !logoFailed) {
            AdaptiveLogoImage(
                logoUrl = logoUrl,
                contentDescription = name,
                modifier = inner,
                onError = { logoFailed = true },
                alignment = Alignment.Center,
            )
        } else if (name.isNotBlank()) {
            Box(inner, contentAlignment = Alignment.Center) {
                CinemaText(
                    text = name,
                    role = TextRole.Label,
                    tone = TextTone.OnMedia,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun DrawScope.arc(
    color: Color,
    start: Float,
    sweep: Float,
    stroke: Float,
) {
    val inset = stroke * 2
    drawArc(
        color = color,
        startAngle = start,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = Offset(inset / 2, inset / 2),
        size =
            androidx.compose.ui.geometry
                .Size(size.width - inset, size.height - inset),
        style = Stroke(stroke, cap = StrokeCap.Round),
    )
}

private fun polygon(
    center: Offset,
    radius: Float,
    corners: Int,
    turnDegrees: Float,
): List<Offset> =
    List(corners) { i ->
        val a = (turnDegrees + i * FULL_TURN / corners) * DEG_TO_RAD
        center + Offset(cos(a), sin(a)) * radius
    }

/** The numbers behind the indicators, apart from drawing (unit tested). */
internal object WaitMath {
    /** How much is held of what playback waits for, 0..1; null while the need is unknown. */
    fun progress(info: BufferingInfo?): Float? =
        info?.takeIf { it.neededMs > 0 }?.let { (it.bufferedMs.toFloat() / it.neededMs).coerceIn(0f, 1f) }

    /** The iris opening for a progress: a little open at 0, fully open at 1. */
    fun irisOpening(progress: Float): Float = IRIS_BREATH_MIN + (1f - IRIS_BREATH_MIN) * progress.coerceIn(0f, 1f)

    /**
     * The reel's speed: [WaitStyle.REEL_TURNS_PER_SECOND] when data arrives as fast as the stream
     * needs it, in proportion when slower or faster (within limits), a crawl when nothing arrives.
     */
    fun reelTurnsPerSecond(info: BufferingInfo?): Float {
        val base = WaitStyle.REEL_TURNS_PER_SECOND
        if (info == null || info.receivingBps <= 0) return base * REEL_IDLE_SHARE
        val need = info.streamBps ?: return base
        return base * (info.receivingBps.toFloat() / need).coerceIn(REEL_SLOWEST_SHARE, REEL_FASTEST_SHARE)
    }

    /** Where a line from [from] (relative to the centre) along [direction] meets the circle of [radius]. */
    fun toCircle(
        from: Offset,
        direction: Offset,
        radius: Float,
    ): Offset {
        val length = direction.getDistance().takeIf { it > 0f } ?: return from
        val d = direction / length
        val along = from.x * d.x + from.y * d.y
        val t = -along + sqrt((along * along - from.getDistanceSquared() + radius * radius).coerceAtLeast(0f))
        return from + d * t
    }

    /**
     * The scene's colour for the indicator, from a small copy of the frame ([pixels], ARGB, rows
     * of [width]): the hue most of the coloured pixels share, made light where the middle of the
     * frame (behind the indicator) is dark and dark where it is light, so it stands out. Null when
     * the frame is too grey to have one (the indicator keeps the accent).
     */
    fun sceneTint(
        pixels: IntArray,
        width: Int,
    ): Int? {
        if (pixels.isEmpty() || width <= 0) return null
        val height = pixels.size / width
        val weights = FloatArray(HUE_BINS)
        val hues = FloatArray(HUE_BINS)
        var luminance = 0f
        var middle = 0
        for ((i, p) in pixels.withIndex()) {
            val r = (p shr 16 and 0xFF) / 255f
            val g = (p shr 8 and 0xFF) / 255f
            val b = (p and 0xFF) / 255f
            val x = i % width
            val y = i / width
            if (x in width / 3 until width - width / 3 && y in height / 3 until height - height / 3) {
                luminance += LUMA_R * r + LUMA_G * g + LUMA_B * b
                middle++
            }
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val saturation = if (max > 0f) (max - min) / max else 0f
            if (saturation < MIN_SATURATION || max < MIN_VALUE) continue
            val hue = hueOf(r, g, b, max, min)
            val bin = (hue / FULL_TURN * HUE_BINS).toInt().coerceIn(0, HUE_BINS - 1)
            val weight = saturation * max
            weights[bin] += weight
            hues[bin] += hue * weight
        }
        val best = weights.indices.maxBy { weights[it] }
        if (weights[best] < pixels.size * MIN_COLOURED_SHARE) return null
        val hue = hues[best] / weights[best]
        val dark = middle == 0 || luminance / middle < HALF
        return hslToArgb(hue, TINT_SATURATION, if (dark) TINT_LIGHT else TINT_DARK)
    }

    private fun hueOf(
        r: Float,
        g: Float,
        b: Float,
        max: Float,
        min: Float,
    ): Float {
        val delta = max - min
        if (delta == 0f) return 0f
        val sector =
            when (max) {
                r -> ((g - b) / delta).mod(SECTORS)
                g -> (b - r) / delta + 2f
                else -> (r - g) / delta + 4f
            }
        return sector * SECTOR_DEGREES
    }

    fun hslToArgb(
        hue: Float,
        saturation: Float,
        lightness: Float,
    ): Int {
        val chroma = (1f - abs(2f * lightness - 1f)) * saturation
        val h = hue / SECTOR_DEGREES
        val x = chroma * (1f - abs(h.mod(2f) - 1f))
        val (r, g, b) =
            when (h.toInt()) {
                0 -> Triple(chroma, x, 0f)
                1 -> Triple(x, chroma, 0f)
                2 -> Triple(0f, chroma, x)
                3 -> Triple(0f, x, chroma)
                4 -> Triple(x, 0f, chroma)
                else -> Triple(chroma, 0f, x)
            }
        val m = lightness - chroma / 2f

        fun channel(v: Float) = ((v + m) * 255f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
    }
}

private const val POLL_MS = 500L
private const val FULL_TURN = 360f
private const val QUARTER_TURN = 90f
private const val DEG_TO_RAD = (PI / 180).toFloat()
private const val NANOS_PER_SECOND = 1_000_000_000f
private const val IRIS_BLADES = 6
private const val IRIS_BLADE_SCALE = 0.9f
private const val IRIS_BREATH_MIN = 0.15f
private const val IRIS_BREATH_MAX = 0.4f
private const val BLADE_TURN = 60f
private const val REEL_SCALE = 0.9f
private const val REEL_HOLES = 5
private const val REEL_HOLE_DISTANCE = 0.52f
private const val REEL_HOLE_SIZE = 0.24f
private const val REEL_HUB_SIZE = 0.1f
private const val REEL_IDLE_SHARE = 0.15f
private const val REEL_SLOWEST_SHARE = 0.25f
private const val REEL_FASTEST_SHARE = 2f
private const val HUE_BINS = 12
private const val SECTORS = 6f
private const val SECTOR_DEGREES = 60f
private const val LUMA_R = 0.2126f
private const val LUMA_G = 0.7152f
private const val LUMA_B = 0.0722f
private const val MIN_SATURATION = 0.25f
private const val MIN_VALUE = 0.15f
private const val MIN_COLOURED_SHARE = 0.03f
private const val HALF = 0.5f
private const val TINT_SATURATION = 0.75f
private const val TINT_LIGHT = 0.7f
private const val TINT_DARK = 0.3f
