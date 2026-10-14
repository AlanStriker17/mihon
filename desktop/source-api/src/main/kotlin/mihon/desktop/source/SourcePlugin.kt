package mihon.desktop.source

import kotlinx.serialization.Serializable

@Serializable
data class SourceDescriptor(
    val id: String,
    val name: String,
    val language: String,
    val baseUrl: String,
    val version: Int = 1,
)

@Serializable
data class SourceManga(
    val id: String,
    val title: String,
    val url: String,
    val thumbnailUrl: String? = null,
    val author: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val status: String? = null,
)

@Serializable
data class SourceChapter(
    val id: String,
    val mangaId: String,
    val name: String,
    val url: String,
    val number: Double? = null,
    val scanlator: String? = null,
    val uploadedAtEpochMillis: Long? = null,
)

@Serializable
data class SourcePage(
    val index: Int,
    val imageUrl: String,
    val headers: Map<String, String> = emptyMap(),
)

@Serializable
data class SourcePageResult<T>(
    val items: List<T>,
    val hasNextPage: Boolean,
)

interface SourcePlugin {
    val descriptor: SourceDescriptor

    suspend fun search(query: String, page: Int): SourcePageResult<SourceManga>

    suspend fun mangaDetails(manga: SourceManga): SourceManga

    suspend fun chapters(manga: SourceManga): List<SourceChapter>

    suspend fun pages(chapter: SourceChapter): List<SourcePage>
}

interface SourcePluginProvider {
    fun create(httpClient: SourceHttpClient): SourcePlugin
}
