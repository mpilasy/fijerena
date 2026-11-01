package org.njarasoa.fijerena.core.ui.sports

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import org.njarasoa.fijerena.core.ui.R
import org.njarasoa.fijerena.core.ui.components.CinemaText
import org.njarasoa.fijerena.core.ui.components.TextRole
import org.njarasoa.fijerena.core.ui.components.TextTone
import org.njarasoa.fijerena.core.ui.di.AppContainer
import org.njarasoa.fijerena.core.ui.history.rememberSourceName
import org.njarasoa.fijerena.core.ui.theme.TimeFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// The question before a channel whose game or programme has not started (Watch now, Remind Me, Add to calendar), the same on TV and phone.

/** The question's title: "Starts Tue 3 Nov · 14:00" ("Today" and "Tomorrow" for those days); "Aired …" for a programme that is over. */
@Composable
fun watchQuestionTitle(startEpochSec: Long): String {
    val resources = LocalResources.current
    val day = Instant.ofEpochSecond(startEpochSec).atZone(ZoneId.systemDefault()).toLocalDate()
    val over = startEpochSec <= System.currentTimeMillis() / 1000
    return stringResource(
        if (over) R.string.watch_question_aired else R.string.watch_question_starts,
        "${shortDayLabel(resources, day)} · ${TimeFormat.formatTime(startEpochSec)}",
    )
}

/** "Today", "Tomorrow", else the short date ("Tue 3 Nov"); Malagasy has its own names. */
internal fun shortDayLabel(
    resources: android.content.res.Resources,
    date: LocalDate,
    today: LocalDate = LocalDate.now(),
    locale: Locale = Locale.getDefault(),
): String =
    if (date == today || date == today.plusDays(1) || locale.language == "mg") {
        gameDayLabel(resources, date, today, locale)
    } else {
        date.format(DateTimeFormatter.ofPattern("EEE d MMM", locale))
    }

/** The question's body: the channel's name on one line, then its source when the device has more than one source. */
@Composable
fun WatchQuestionBody(
    channelName: String,
    providerId: Long,
) {
    val context = LocalContext.current.applicationContext
    val sources by remember(context) { AppContainer.getInstance(context).providerRepository.getAllProviders() }
        .collectAsState(initial = emptyList())
    val sourceName = rememberSourceName(providerId)
    Column {
        CinemaText(
            shownChannelName(channelName),
            role = TextRole.Body,
            tone = TextTone.Inherit,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (sources.size > 1 && sourceName != null) {
            CinemaText(
                stringResource(R.string.watch_question_source, sourceName),
                role = TextRole.Body,
                tone = TextTone.Secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
