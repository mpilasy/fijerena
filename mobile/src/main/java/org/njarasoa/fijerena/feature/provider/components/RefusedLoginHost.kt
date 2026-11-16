package org.njarasoa.fijerena.feature.provider.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.njarasoa.fijerena.core.network.provider.RefusedLogins
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.DialogAction
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.utils.refusedLoginSource
import org.njarasoa.fijerena.ui.components.CinemaAlertDialog

/**
 * Over any screen: a source's server refused the saved password ([RefusedLogins]). An Admin gets
 * Enter Password, which opens the source's form on its password field ([onEnterPassword]); anyone
 * else is told to ask an admin. Not while a source form is open ([onSourceForm]).
 */
@Composable
fun RefusedLoginHost(
    admin: Boolean,
    onSourceForm: Boolean,
    onEnterPassword: (providerId: Long) -> Unit,
) {
    val source = refusedLoginSource() ?: return
    if (onSourceForm) return
    val close = { RefusedLogins.dismiss(source.id) }
    CinemaAlertDialog(
        onDismissRequest = close,
        title = stringResource(R.string.login_refused_title, source.name),
        text = {
            CinemaText(
                stringResource(if (admin) R.string.login_refused_text else R.string.login_refused_ask_admin, source.name),
                role = TextRole.Body,
            )
        },
        main =
            if (admin) {
                DialogAction(stringResource(R.string.login_refused_enter_password)) {
                    close()
                    onEnterPassword(source.id)
                }
            } else {
                null
            },
        cancel = DialogAction(stringResource(if (admin) R.string.login_refused_not_now else R.string.common_ok), onClick = close),
    )
}
