package eu.kanade.tachiyomi.util.system

import androidx.fragment.app.FragmentActivity
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.authenticate
import eu.kanade.tachiyomi.util.system.AuthenticatorUtil.isAuthenticationSupported
import eu.kanade.tachiyomi.util.system.HapticFeedbackManager.Event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR

object AdultContentVault {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableUnlocked = MutableStateFlow(false)
    val unlocked = mutableUnlocked.asStateFlow()

    suspend fun unlock(activity: FragmentActivity): Boolean {
        if (mutableUnlocked.value) return true
        if (!activity.isAuthenticationSupported()) {
            activity.toast(MR.strings.adult_content_unlock_setup)
            return false
        }

        val authenticated = activity.authenticate(
            title = activity.stringResource(MR.strings.adult_content_unlock_title),
            subtitle = activity.stringResource(MR.strings.adult_content_unlock_summary),
        )
        if (!authenticated) return false

        mutableUnlocked.value = true
        HapticFeedbackManager.perform(activity, Event.CATEGORY_UNLOCK)
        scope.launch {
            delay(5 * 60 * 1000L)
            mutableUnlocked.value = false
        }
        return true
    }
}
