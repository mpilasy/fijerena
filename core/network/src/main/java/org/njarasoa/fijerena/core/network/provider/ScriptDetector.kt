package org.njarasoa.fijerena.core.network.provider

import kotlinx.serialization.Serializable

/**
 * Unicode script types for category language filtering.
 */
@Serializable
enum class ScriptType(
    val displayName: String,
) {
    LATIN("Latin"),
    ARABIC("Arabic"),
    CYRILLIC("Cyrillic"),
    GREEK("Greek"),
}

/**
 * Detects the dominant Unicode script of a text string.
 * Uses majority-vote sampling of up to 10 alphabetic characters.
 */
object ScriptDetector {
    fun detectScript(text: String): ScriptType {
        val votes = mutableMapOf<ScriptType, Int>()
        var sampled = 0

        for (ch in text) {
            if (sampled >= 10) break
            if (!Character.isLetter(ch)) continue
            sampled++
            val script = classifyChar(ch)
            votes[script] = (votes[script] ?: 0) + 1
        }

        if (votes.isEmpty()) return ScriptType.LATIN

        return votes.maxByOrNull { it.value }?.key ?: ScriptType.LATIN
    }

    private fun classifyChar(ch: Char): ScriptType {
        val block = Character.UnicodeBlock.of(ch) ?: return ScriptType.LATIN
        return when (block) {
            Character.UnicodeBlock.ARABIC,
            Character.UnicodeBlock.ARABIC_SUPPLEMENT,
            Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_A,
            Character.UnicodeBlock.ARABIC_PRESENTATION_FORMS_B,
            -> ScriptType.ARABIC

            Character.UnicodeBlock.CYRILLIC,
            Character.UnicodeBlock.CYRILLIC_SUPPLEMENTARY,
            Character.UnicodeBlock.CYRILLIC_EXTENDED_A,
            Character.UnicodeBlock.CYRILLIC_EXTENDED_B,
            -> ScriptType.CYRILLIC

            Character.UnicodeBlock.GREEK,
            Character.UnicodeBlock.GREEK_EXTENDED,
            -> ScriptType.GREEK

            Character.UnicodeBlock.BASIC_LATIN,
            Character.UnicodeBlock.LATIN_1_SUPPLEMENT,
            Character.UnicodeBlock.LATIN_EXTENDED_A,
            Character.UnicodeBlock.LATIN_EXTENDED_B,
            Character.UnicodeBlock.LATIN_EXTENDED_ADDITIONAL,
            -> ScriptType.LATIN

            else -> ScriptType.LATIN
        }
    }
}
