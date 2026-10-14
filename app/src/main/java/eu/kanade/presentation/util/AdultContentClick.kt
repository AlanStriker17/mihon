package eu.kanade.presentation.util

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import eu.kanade.domain.base.AdultContentMode
import eu.kanade.tachiyomi.util.system.AdultContentVault
import kotlinx.coroutines.launch
import tachiyomi.domain.manga.model.MangaCover

@Composable
internal fun rememberAdultContentHidden(cover: MangaCover): Boolean {
    val unlocked by AdultContentVault.unlocked.collectAsState()
    return cover.isSensitive &&
        AdultContentMode.from(cover.adultContentMode) == AdultContentMode.HIDE &&
        !unlocked
}

@Composable
internal fun rememberAdultContentClick(
    isSensitive: Boolean,
    onClick: () -> Unit,
): () -> Unit {
    val context = LocalContext.current.findFragmentActivity()
    val scope = rememberCoroutineScope()
    val unlocked by AdultContentVault.unlocked.collectAsState()

    return remember(isSensitive, onClick, context, unlocked) {
        {
            if (!isSensitive || unlocked) {
                onClick()
            } else {
                context?.let { activity ->
                    scope.launch {
                        if (AdultContentVault.unlock(activity)) onClick()
                    }
                }
            }
        }
    }
}

internal tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
