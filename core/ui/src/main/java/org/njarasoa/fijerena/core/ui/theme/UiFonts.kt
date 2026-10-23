package org.njarasoa.fijerena.core.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import org.njarasoa.fijerena.core.ui.R

/**
 * The typefaces the Looks bring, shipped in the app under their open licences (licenses/fonts/):
 * variable fonts, sampled at each weight the type scale uses. Nothing is downloaded.
 */
object UiFonts {
    private val weights = listOf(400, 500, 600, 700, 800)

    @OptIn(ExperimentalTextApi::class)
    private fun variable(res: Int): FontFamily =
        FontFamily(weights.map { Font(res, FontWeight(it), variationSettings = FontVariation.Settings(FontVariation.weight(it))) })

    val Inter = variable(R.font.inter_variable)
    val Manrope = variable(R.font.manrope_title_variable)
    val Lexend = variable(R.font.lexend_variable)
    val SpaceGrotesk = variable(R.font.space_grotesk_variable)
    val Nunito = variable(R.font.nunito_variable)
    val IbmPlexSans = variable(R.font.ibm_plex_sans_variable)

    /** Static cuts at the weights Sharp uses (its regular is 500, its bold 800). */
    val Barlow =
        FontFamily(
            Font(R.font.barlow_medium, FontWeight(500)),
            Font(R.font.barlow_semibold, FontWeight(600)),
            Font(R.font.barlow_extrabold, FontWeight(800)),
        )

    /** One weight only: Sharp's titles set [UiTypeTokens.titleWeight] to 600. */
    val BarlowCondensed = FontFamily(Font(R.font.barlow_condensed_semibold, FontWeight(600)))

    /** Console's condensed capitals: a variable font with weights 100–700, sampled at 400–700. */
    @OptIn(ExperimentalTextApi::class)
    val Antonio =
        FontFamily(
            listOf(400, 500, 600, 700).map {
                Font(R.font.antonio_variable, FontWeight(it), variationSettings = FontVariation.Settings(FontVariation.weight(it)))
            },
        )

    /** Meadow's titles: one weight, cut to Latin; a Look using it sets [UiTypeTokens.titleWeight] to 600. */
    val KleeOne = FontFamily(Font(R.font.klee_one_semibold, FontWeight(600)))

    /** Meadow's text: rounded, static cuts cut to Latin. */
    val ZenMaruGothic =
        FontFamily(
            Font(R.font.zen_maru_gothic_regular, FontWeight(400)),
            Font(R.font.zen_maru_gothic_medium, FontWeight(500)),
            Font(R.font.zen_maru_gothic_bold, FontWeight(700)),
        )

    /** One weight only: a Look using it for titles sets [UiTypeTokens.titleWeight] to 400, so nothing is faux-bolded. */
    val DmSerifDisplay = FontFamily(Font(R.font.dm_serif_display, FontWeight.Normal))
}
