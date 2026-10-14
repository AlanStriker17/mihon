package eu.kanade.presentation.library.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import eu.kanade.tachiyomi.source.model.SManga
import mihon.icons.materialsymbols.MaterialSymbols
import mihon.icons.materialsymbols.rounded.Folder
import tachiyomi.presentation.core.components.Badge
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
internal fun DownloadsBadge(count: Int) {
    if (count > 0) {
        Badge(
            text = "$count",
            color = MaterialTheme.colorScheme.tertiary,
            textColor = MaterialTheme.colorScheme.onTertiary,
        )
    }
}

@Composable
internal fun UnreadBadge(count: Long) {
    if (count > 0) {
        Badge(text = "$count")
    }
}

@Composable
internal fun LanguageBadge(
    isLocal: Boolean,
    sourceLanguage: String,
) {
    if (isLocal) {
        Badge(
            imageVector = MaterialSymbols.Rounded.Folder,
            color = MaterialTheme.colorScheme.tertiary,
            iconColor = MaterialTheme.colorScheme.onTertiary,
        )
    } else if (sourceLanguage.isNotEmpty()) {
        Badge(
            text = sourceLanguage.uppercase(),
            color = MaterialTheme.colorScheme.tertiary,
            textColor = MaterialTheme.colorScheme.onTertiary,
        )
    }
}

@Composable
internal fun MangaStatusBadge(status: Long) {
    val label = when (status) {
        SManga.COMPLETED.toLong(),
        SManga.PUBLISHING_FINISHED.toLong(),
        -> stringResource(MR.strings.status_badge_completed)
        SManga.ON_HIATUS.toLong() -> stringResource(MR.strings.status_badge_hiatus)
        SManga.CANCELLED.toLong() -> stringResource(MR.strings.status_badge_dropped)
        SManga.ONGOING.toLong() -> stringResource(MR.strings.status_badge_ongoing)
        else -> return
    }
    Badge(
        text = label,
        color = MaterialTheme.colorScheme.secondaryContainer,
        textColor = MaterialTheme.colorScheme.onSecondaryContainer,
    )
}

@PreviewLightDark
@Composable
private fun BadgePreview() {
    TachiyomiPreviewTheme {
        Column {
            DownloadsBadge(count = 10)
            UnreadBadge(count = 10)
            LanguageBadge(isLocal = true, sourceLanguage = "EN")
            LanguageBadge(isLocal = false, sourceLanguage = "EN")
        }
    }
}
