package eu.kanade.presentation.manga.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import mihon.app.SmartCategorizer
import mihon.app.di.appGraph
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.Manga as MangaModel
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun SmartMangaInsights(
    manga: Manga,
    sourceName: String,
    sourceLanguage: String,
    recommendations: List<Manga>,
) {
    val context = LocalContext.current
    val category = remember(manga, sourceName, sourceLanguage) {
        SmartCategorizer.classify(manga, sourceName, sourceLanguage)
    }
    val matches = remember(manga, recommendations) {
        recommendations.mapNotNull { candidate ->
            val score = recommendationScore(manga, candidate)
            if (score == 0) null else candidate to score
        }
            .sortedByDescending { it.second }
            .take(12)
    }
    var showRecommendations by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(MR.strings.smart_category, category.categoryName),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(
                MR.strings.smart_recommended_sources,
                category.recommendedSources.joinToString(),
            ),
            style = MaterialTheme.typography.bodySmall,
        )
        if (category.isAdult) {
            val lockEnabled = remember { context.appGraph.securityPreferences.useAuthenticator.get() }
            Text(
                text = stringResource(
                    if (lockEnabled) {
                        MR.strings.smart_adult_lock_enabled
                    } else {
                        MR.strings.smart_adult_lock_disabled
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (matches.isNotEmpty()) {
            OutlinedButton(onClick = { showRecommendations = true }) {
                Text(stringResource(MR.strings.recommendations_title))
            }
        }
    }

    if (showRecommendations) {
        ModalBottomSheet(onDismissRequest = { showRecommendations = false }) {
            Text(
                text = stringResource(MR.strings.recommendations_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            LazyColumn {
                items(matches, key = { it.first.id }) { (candidate, score) ->
                    val matchingAuthor = manga.author?.takeIf {
                        it.isNotBlank() && candidate.author.equals(it, ignoreCase = true)
                    }
                    ListItem(
                        headlineContent = { Text(candidate.title) },
                        supportingContent = {
                            Text(
                                when {
                                    matchingAuthor != null ->
                                        stringResource(MR.strings.recommendation_same_author, matchingAuthor)
                                    score >= 10 -> stringResource(MR.strings.recommendation_shared_tags)
                                    else -> stringResource(MR.strings.recommendation_shared_storyline)
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}

private fun recommendationScore(current: MangaModel, candidate: MangaModel): Int {
    var score = 0
    if (!current.author.isNullOrBlank() && current.author.equals(candidate.author, ignoreCase = true)) score += 100
    val currentTags = current.genre.orEmpty().map { it.trim().lowercase() }.filter(String::isNotBlank).toSet()
    val candidateTags = candidate.genre.orEmpty().map { it.trim().lowercase() }.filter(String::isNotBlank).toSet()
    score += currentTags.intersect(candidateTags).size * 10
    val keywords = Regex("""[\p{L}\p{N}]{4,}""")
    val currentStory = keywords.findAll("${current.title} ${current.description.orEmpty()}")
        .map { it.value.lowercase() }
        .toSet()
    val candidateStory = keywords.findAll("${candidate.title} ${candidate.description.orEmpty()}")
        .map { it.value.lowercase() }
        .toSet()
    score += currentStory.intersect(candidateStory).size
    return score
}
