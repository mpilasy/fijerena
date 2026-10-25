package org.njarasoa.fijerena.core.ui.components

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import org.njarasoa.fijerena.core.ui.theme.CinemaTheme
import org.njarasoa.fijerena.core.ui.theme.LocalUiStyle

/** What a piece of text is (decision D1): its size and weight come with it. */
enum class TextRole {
    Display,
    Title,
    Heading,
    Body,
    BodySmall,
    Label,
    Caption,
}

/** What a piece of text says about itself: its colour comes with it. */
enum class TextTone {
    Primary,
    Secondary,
    Tertiary,
    Disabled,
    Accent,
    AccentSoft,
    Danger,
    Success,
    Warning,
    Live,
    Highlight,
    OnAccent,

    /** On the danger fill (a warning banner). */
    OnDanger,

    /** On the TV's focus fill (a focused tab or chip): dark on the white or cream fill. */
    OnFocus,

    /** Over video or artwork: white whatever the theme. */
    OnMedia,

    /** The colour around it: a button's or a chip's content colour. */
    Inherit,
}

/** [role]'s text style, from [CinemaTheme]. */
@Composable
@ReadOnlyComposable
fun textStyleOf(role: TextRole): TextStyle {
    val type = CinemaTheme.type
    return when (role) {
        TextRole.Display -> type.display
        TextRole.Title -> type.title
        TextRole.Heading -> type.heading
        TextRole.Body -> type.body
        TextRole.BodySmall -> type.bodySmall
        TextRole.Label -> type.label
        TextRole.Caption -> type.caption
    }
}

/** [tone]'s colour, from [CinemaTheme]; [TextTone.Inherit] is the surrounding content colour. */
@Composable
@ReadOnlyComposable
fun colorOf(tone: TextTone): Color {
    val c = CinemaTheme.colors
    return when (tone) {
        TextTone.Primary -> c.textPrimary
        TextTone.Secondary -> c.textSecondary
        TextTone.Tertiary -> c.textTertiary
        TextTone.Disabled -> c.textDisabled
        TextTone.Accent -> c.accent
        TextTone.AccentSoft -> c.accentSoft
        TextTone.Danger -> c.danger
        TextTone.Success -> c.success
        TextTone.Warning -> c.warning
        TextTone.Live -> c.live
        TextTone.Highlight -> c.highlight
        TextTone.OnAccent -> c.onAccent
        TextTone.OnFocus -> c.onFocusFill
        TextTone.OnMedia -> c.onMedia
        TextTone.OnDanger -> c.onDanger
        TextTone.Inherit -> LocalContentColor.current
    }
}

/**
 * The tone [CinemaText] takes when its caller names none: [TextTone.Primary], except inside a
 * button, which provides [TextTone.Inherit] so a label follows the button's own colour (dark on a
 * focused button's light ground) without each label having to say so.
 */
val LocalTextTone = staticCompositionLocalOf { TextTone.Primary }

/**
 * The app's text, everywhere: [role] gives its size and weight, [tone] its colour, [emphasis] the
 * Look and Feel's emphasis weight. Nothing else about its look is set by the caller, so a changed
 * colour theme or Look and Feel reaches every piece of text. [keepCase]: the text is content (a
 * search term, a category or title name), so a Look that puts labels in capitals does not change it.
 */
@Composable
fun CinemaText(
    text: String,
    modifier: Modifier = Modifier,
    role: TextRole = TextRole.Body,
    tone: TextTone = LocalTextTone.current,
    emphasis: Boolean = false,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    strikethrough: Boolean = false,
    italic: Boolean = false,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    keepCase: Boolean = false,
) {
    val shown = if (keepCase) text else inLookCase(text, role)
    // look-ok: the component that wraps Material's Text
    Text(
        text = shown,
        // A screen reader (and a focus walk) reads the words as written, not spelt-out capitals.
        modifier = if (shown == text) modifier else modifier.clearAndSetSemantics { this.text = AnnotatedString(text) },
        style = resolvedStyle(role, emphasis, strikethrough, italic),
        color = colorOf(tone),
        textAlign = textAlign ?: LocalTextStyle.current.textAlign,
        maxLines = maxLines,
        minLines = minLines,
        overflow = overflow,
        softWrap = softWrap,
        onTextLayout = onTextLayout,
    )
}

/**
 * The height that [lines] lines of [role] text take, measured in the current Look and Feel, font
 * size and Text & Grid Size. A cell of fixed height uses it to make sure that its text fits.
 */
@Composable
fun rememberTextHeight(
    role: TextRole,
    lines: Int = 1,
    emphasis: Boolean = false,
): Dp {
    val measurer = rememberTextMeasurer()
    val style = resolvedStyle(role, emphasis, strikethrough = false, italic = false)
    val density = LocalDensity.current
    return remember(measurer, style, density, lines) {
        val sample = List(lines) { TEXT_HEIGHT_SAMPLE }.joinToString("\n")
        val height = measurer.measure(sample, style, maxLines = lines).size.height
        with(density) { height.toDp() }
    }
}

/** Tall and deep letters, so the measured line holds every glyph. */
private const val TEXT_HEIGHT_SAMPLE = "ÁÉgjpqy"

/** [CinemaText] for styled text (spans set their own emphasis or tone through the theme's roles). */
@Composable
fun CinemaText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    role: TextRole = TextRole.Body,
    tone: TextTone = LocalTextTone.current,
    emphasis: Boolean = false,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
) {
    // look-ok: the component that wraps Material's Text
    Text(
        text = text,
        modifier = modifier,
        style = resolvedStyle(role, emphasis, false, false),
        color = colorOf(tone),
        textAlign = textAlign ?: LocalTextStyle.current.textAlign,
        maxLines = maxLines,
        minLines = minLines,
        overflow = overflow,
        softWrap = softWrap,
        onTextLayout = onTextLayout ?: {},
    )
}

/** [text] as [role] shows it in the Look: in capitals for a label when the Look says so (Console). */
@Composable
@ReadOnlyComposable
internal fun inLookCase(
    text: String,
    role: TextRole,
): String = lookCase(text, role, LocalUiStyle.current.type.capsLabels, LocalConfiguration.current.locales[0])

/** [text] in capitals when [caps] and [role] is a label; titles, headings and body keep their case. */
internal fun lookCase(
    text: String,
    role: TextRole,
    caps: Boolean,
    locale: java.util.Locale,
): String = if (caps && role == TextRole.Label) text.uppercase(locale) else text

/** The Look and Feel's emphasis weight, for a span inside [CinemaText] styled text. */
val emphasisWeight: FontWeight
    @Composable @ReadOnlyComposable
    get() = FontWeight(LocalUiStyle.current.type.weightEmphasis)

@Composable
@ReadOnlyComposable
private fun resolvedStyle(
    role: TextRole,
    emphasis: Boolean,
    strikethrough: Boolean,
    italic: Boolean,
): TextStyle {
    val base = textStyleOf(role)
    if (!emphasis && !strikethrough && !italic) return base
    return base.copy(
        fontWeight = if (emphasis) emphasisWeight else base.fontWeight,
        textDecoration = if (strikethrough) TextDecoration.LineThrough else base.textDecoration,
        fontStyle = if (italic) FontStyle.Italic else base.fontStyle,
    )
}
