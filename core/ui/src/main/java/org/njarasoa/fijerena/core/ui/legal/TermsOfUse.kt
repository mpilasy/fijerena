package org.njarasoa.fijerena.core.ui.legal

import org.njarasoa.fijerena.core.ui.R

/**
 * Settings → About → Terms of use, the same on TV and phone: each section's title and text, in
 * order. TERMS.md at the repository root holds the same text; change both together.
 */
object TermsOfUse {
    val sections: List<Pair<Int, Int>> =
        listOf(
            R.string.terms_s1_title to R.string.terms_s1_body,
            R.string.terms_s2_title to R.string.terms_s2_body,
            R.string.terms_s3_title to R.string.terms_s3_body,
            R.string.terms_s4_title to R.string.terms_s4_body,
            R.string.terms_s5_title to R.string.terms_s5_body,
            R.string.terms_s6_title to R.string.terms_s6_body,
            R.string.terms_s7_title to R.string.terms_s7_body,
            R.string.terms_s8_title to R.string.terms_s8_body,
        )
}
