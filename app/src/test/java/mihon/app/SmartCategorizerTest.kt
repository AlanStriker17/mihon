package mihon.app

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SmartCategorizerTest {

    @Test
    fun `classifies adult manhwa and preserves webtoon mode`() {
        val result = SmartCategorizer.classify(
            title = "Regression of the Smut System",
            genres = listOf("Manhwa", "Romance"),
            description = "",
            sourceName = "Asura Scans",
            sourceLanguage = "en",
        )

        result shouldBe SmartCategorizer.Type.ADULT_MANHWA
        result.readerMode shouldBe SmartCategorizer.ReaderMode.CONTINUOUS_VERTICAL
    }

    @Test
    fun `classifies hentai ahead of general format`() {
        SmartCategorizer.classify(
            title = "Hentai title",
            genres = listOf("Manga"),
            description = "",
            sourceName = "Manga source",
            sourceLanguage = "ja",
        ) shouldBe SmartCategorizer.Type.HENTAI
    }

    @Test
    fun `uses language and story keywords as format signals`() {
        SmartCategorizer.classify(
            title = "The Immortal Murim System",
            genres = emptyList(),
            description = "",
            sourceName = "Unknown source",
            sourceLanguage = "en",
        ) shouldBe SmartCategorizer.Type.MANHWA

        SmartCategorizer.classify(
            title = "Regression System",
            genres = emptyList(),
            description = "",
            sourceName = "English source",
            sourceLanguage = "en",
        ) shouldBe SmartCategorizer.Type.MANHWA

        SmartCategorizer.classify(
            title = "Chinese title",
            genres = emptyList(),
            description = "",
            sourceName = "Unknown source",
            sourceLanguage = "zh",
        ) shouldBe SmartCategorizer.Type.MANHUA
    }

    @Test
    fun `keeps ordinary isekai manga as manga`() {
        SmartCategorizer.classify(
            title = "Isekai Journey",
            genres = listOf("Adventure"),
            description = "",
            sourceName = "Japanese source",
            sourceLanguage = "ja",
        ) shouldBe SmartCategorizer.Type.MANGA
    }

    @Test
    fun `recommends format-specific sources`() {
        SmartCategorizer.Type.MANHWA.recommendedSources shouldContain "Asura Scans"
        SmartCategorizer.Type.MANHUA.recommendedSources shouldContain "Mangakakalot"
    }

    @Test
    fun `finds only other sources with more chapters`() {
        SmartCategorizer.higherChapterSources(
            currentSourceName = "Primary",
            currentChapterCount = 20,
            otherSources = listOf(
                SmartCategorizer.SourceChapterCount("Primary", 40),
                SmartCategorizer.SourceChapterCount("Secondary", 28),
                SmartCategorizer.SourceChapterCount("Secondary", 30),
                SmartCategorizer.SourceChapterCount("Older", 18),
            ),
        ) shouldContainExactly listOf(
            SmartCategorizer.HigherChapterSource("Secondary", 30, 10),
        )
    }
}
