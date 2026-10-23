package org.njarasoa.fijerena.core.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType

/**
 * The keyboard each kind of text field asks for. A plain text keyboard (Gboard and most others)
 * capitalises, corrects and puts a space after every dot, which breaks addresses and logins.
 */
object FieldKeyboards {
    /** URLs, hosts, share names: the URL layout, no capitals, no corrections, no space after a dot. */
    val Url = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false)

    /** User names: often an email or have dots, so the email layout, which leaves them as typed. */
    val Username = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false)

    /** Passwords, secrets, API keys: hidden from suggestions and learning. */
    val Password = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false)

    /** Names of things (profile, source, device, guide): each word capitalised. */
    val Name = KeyboardOptions(capitalization = KeyboardCapitalization.Words)

    /** Short codes shown in capitals, like a source's short name. */
    val Code = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false)

    /** Whole numbers that may be negative: the phone pad, the number pad that always has a minus. */
    val SignedNumber = KeyboardOptions(keyboardType = KeyboardType.Phone)
}
