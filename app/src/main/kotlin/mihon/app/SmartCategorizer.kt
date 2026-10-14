package mihon.app

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tachiyomi.domain.category.interactor.CreateCategoryWithName
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.manga.model.Manga

object SmartCategorizer {

    enum class Type(
        val categoryName: String,
        val isAdult: Boolean,
        val readerMode: ReaderMode,
        val recommendedSources: List<String>,
    ) {
        MANGA("Manga", false, ReaderMode.SINGLE_PAGE, listOf("MangaDex", "MangaPlus")),
        MANHWA(
            "Manhwa",
            false,
            ReaderMode.CONTINUOUS_VERTICAL,
            listOf("Asura Scans", "Flame Comics", "Reaper Scans"),
        ),
        MANHUA(
            "Manhua",
            false,
            ReaderMode.CONTINUOUS_VERTICAL,
            listOf("Mangakakalot", "ManhuaPlus", "MangaDex"),
        ),
        HENTAI("Hentai", true, ReaderMode.SINGLE_PAGE, listOf("Mangakakalot", "MangaDex")),
        ADULT_MANHWA(
            "Adult Manhwa",
            true,
            ReaderMode.CONTINUOUS_VERTICAL,
            listOf("Asura Scans", "Lezhin", "Toomics"),
        ),
    }

    enum class ReaderMode {
        SINGLE_PAGE,
        CONTINUOUS_VERTICAL,
    }

    data class SourceChapterCount(
        val sourceName: String,
        val chapterCount: Long,
    )

    data class HigherChapterSource(
        val sourceName: String,
        val chapterCount: Long,
        val additionalChapters: Long,
    )

    private val categoryCreationMutex = Mutex()

    private val hentaiKeywords = keywordPatterns(
        "hentai",
        "smut",
        "18+",
        "nsfw",
        "porn",
        "explicit",
        "erotica",
        "ecchi",
        "adult",
    )
    private val explicitHentaiKeywords = keywordPatterns("hentai")

    private val manhwaKeywords = keywordPatterns("manhwa", "webtoon", "korean comics")
    private val manhuaKeywords = keywordPatterns("manhua", "chinese comics")
    private val murimKeywords = keywordPatterns("murim")
    private val manhwaStoryKeywords = keywordPatterns("transmigration", "regression", "system", "isekai")

    fun classify(manga: Manga, sourceName: String, sourceLanguage: String): Type {
        return classify(
            title = manga.title,
            genres = manga.genre.orEmpty(),
            description = manga.description.orEmpty(),
            sourceName = sourceName,
            sourceLanguage = sourceLanguage,
        )
    }

    fun classify(
        title: String,
        genres: List<String>,
        description: String,
        sourceName: String,
        sourceLanguage: String,
    ): Type {
        val metadata = listOf(title, genres.joinToString(" "), description, sourceName).joinToString(" ")
        val explicitHentai = hasAny(explicitHentaiKeywords, metadata)
        val isManhwa = hasAny(manhwaKeywords, metadata) ||
            sourceLanguage.equals("ko", ignoreCase = true) ||
            hasAny(murimKeywords, metadata) ||
            (
                !sourceLanguage.equals("ja", ignoreCase = true) &&
                    hasAny(manhwaStoryKeywords, metadata)
                )
        val isManhua = hasAny(manhuaKeywords, metadata) ||
            sourceLanguage.equals("zh", ignoreCase = true) ||
            sourceLanguage.equals("cn", ignoreCase = true) ||
            sourceLanguage.equals("tw", ignoreCase = true)
        val isAdult = hasAny(hentaiKeywords, metadata)

        return when {
            explicitHentai -> Type.HENTAI
            isManhwa && isAdult -> Type.ADULT_MANHWA
            isAdult -> Type.HENTAI
            isManhua -> Type.MANHUA
            isManhwa -> Type.MANHWA
            else -> Type.MANGA
        }
    }

    fun isSmartCategoryName(name: String): Boolean {
        return Type.entries.any { it.categoryName.equals(name, ignoreCase = true) }
    }

    fun higherChapterSources(
        currentSourceName: String,
        currentChapterCount: Long,
        otherSources: List<SourceChapterCount>,
    ): List<HigherChapterSource> {
        if (currentChapterCount < 0) return emptyList()

        return otherSources
            .asSequence()
            .filter { it.chapterCount > currentChapterCount }
            .filterNot { it.sourceName.equals(currentSourceName, ignoreCase = true) }
            .groupBy { it.sourceName.trim().lowercase() }
            .values
            .mapNotNull { matches ->
                val highestCount = matches.maxOfOrNull { it.chapterCount } ?: return@mapNotNull null
                val sourceName = matches.first().sourceName.trim()
                HigherChapterSource(
                    sourceName = sourceName,
                    chapterCount = highestCount,
                    additionalChapters = highestCount - currentChapterCount,
                )
            }
            .sortedByDescending { it.chapterCount }
            .toList()
    }

    suspend fun findOrCreateCategoryId(
        type: Type,
        getCategories: suspend () -> List<Category>,
        createCategory: suspend (String) -> CreateCategoryWithName.Result,
    ): Long? = categoryCreationMutex.withLock {
        getCategories().firstOrNull { it.name.equals(type.categoryName, ignoreCase = true) }?.let {
            return@withLock it.id
        }

        when (createCategory(type.categoryName)) {
            CreateCategoryWithName.Result.Success -> {
                getCategories()
                    .firstOrNull { it.name.equals(type.categoryName, ignoreCase = true) }
                    ?.id
                    ?: error("Category '${type.categoryName}' was not available after creation")
            }
            is CreateCategoryWithName.Result.InternalError -> null
        }
    }

    private fun hasAny(patterns: List<Regex>, text: String): Boolean {
        return patterns.any { it.containsMatchIn(text) }
    }

    private fun keywordPatterns(vararg keywords: String): List<Regex> {
        return keywords.map {
            Regex(
                pattern = "(?<![\\p{L}\\p{N}])${Regex.escape(it)}(?![\\p{L}\\p{N}])",
                option = RegexOption.IGNORE_CASE,
            )
        }
    }
}
