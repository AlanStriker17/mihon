package eu.kanade.presentation.more

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.BuildConfig
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

private val featureEntries = listOf(
    MR.strings.custom_feature_status_badges,
    MR.strings.custom_feature_extension_restore,
    MR.strings.custom_feature_recommendations,
    MR.strings.custom_feature_adult_vault,
    MR.strings.custom_feature_backup_share,
    MR.strings.custom_feature_arc_groups,
    MR.strings.custom_feature_smart_categorization,
)

@Composable
fun CustomFeatureChangelogDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(MR.strings.custom_features_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                featureEntries.forEach { feature ->
                    Text(
                        text = stringResource(feature),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.action_ok))
            }
        },
    )
}

@Composable
fun FirstLaunchFeatureSummary() {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }
    val preferences = remember(context) {
        context.getSharedPreferences("custom_feature_changelog", 0)
    }

    LaunchedEffect(preferences) {
        showDialog = preferences.getString("shown_version", null) != BuildConfig.VERSION_NAME
    }

    if (showDialog) {
        CustomFeatureChangelogDialog(
            onDismiss = {
                preferences.edit().putString("shown_version", BuildConfig.VERSION_NAME).apply()
                showDialog = false
            },
        )
    }
}
