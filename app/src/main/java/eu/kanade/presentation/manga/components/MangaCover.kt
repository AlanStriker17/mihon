package eu.kanade.presentation.manga.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import eu.kanade.domain.base.AdultContentMode
import eu.kanade.presentation.util.rememberResourceBitmapPainter
import eu.kanade.presentation.util.rememberAdultContentClick
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.system.AdultContentVault
import tachiyomi.domain.manga.model.MangaCover as MangaCoverModel
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

enum class MangaCover(val ratio: Float) {
    Square(1f / 1f),
    Book(2f / 3f),
    ;

    @Composable
    operator fun invoke(
        data: Any?,
        modifier: Modifier = Modifier,
        contentDescription: String = "",
        shape: Shape = MaterialTheme.shapes.extraSmall,
        onClick: (() -> Unit)? = null,
    ) {
        val cover = data as? MangaCoverModel
        val isSensitive = cover?.isSensitive == true
        val mode = AdultContentMode.from(cover?.adultContentMode ?: AdultContentMode.SHOW.value)
        val unlocked by AdultContentVault.unlocked.collectAsState()
        val hidden = isSensitive && mode == AdultContentMode.HIDE && !unlocked
        val blurCover = isSensitive && mode != AdultContentMode.HIDE && !unlocked
        val safeOnClick = onClick?.let { rememberAdultContentClick(isSensitive, it) }
        val privacyTitle = cover?.privacyTitle

        val coverModifier = modifier
            .aspectRatio(ratio)
            .clip(shape)
            .then(
                if (safeOnClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = safeOnClick)
                } else {
                    Modifier
                },
            )
        if (privacyTitle != null) {
            Box(
                modifier = coverModifier.background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (hidden) stringResource(MR.strings.adult_content_hidden_title) else privacyTitle,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            AsyncImage(
                model = if (hidden) null else data,
                placeholder = ColorPainter(CoverPlaceholderColor),
                error = rememberResourceBitmapPainter(id = R.drawable.cover_error),
                contentDescription = contentDescription,
                modifier = coverModifier.blur(if (blurCover) 24.dp else 0.dp),
                contentScale = ContentScale.Crop,
            )
        }
        if (hidden && privacyTitle == null) {
            Box(
                modifier = modifier
                    .aspectRatio(ratio)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(MR.strings.adult_content_hidden_title))
            }
        }
    }
}

private val CoverPlaceholderColor = Color(0x1F888888)
