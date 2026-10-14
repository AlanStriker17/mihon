package eu.kanade.tachiyomi.ui.manga

import eu.kanade.tachiyomi.data.download.model.Download
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.chapter.model.Chapter

class ChapterAggregatorTest {

    @Test
    fun `groups duplicate chapter numbers and preserves source entries`() {
        val result = ChapterAggregator.aggregate(
            listOf(item(1, 4.0), item(2, 4.0), item(3, 5.0)),
        )

        result.map { row ->
            (row as ChapterAggregator.Row.Chapters).items.map { it.id }
        } shouldBe listOf(listOf(1L, 2L), listOf(3L))
    }

    @Test
    fun `does not group unnumbered chapters`() {
        val result = ChapterAggregator.aggregate(
            listOf(item(1, -1.0), item(2, -1.0)),
        )

        result.size shouldBe 2
    }

    @Test
    fun `uses page count when supplied and a conservative default otherwise`() {
        ReadingTimeEstimator.estimateMinutes() shouldBe 4
        ReadingTimeEstimator.estimateMinutes(pageCount = 24) shouldBe 4
        ReadingTimeEstimator.estimateMinutes(pageCount = 1) shouldBe 1
    }

    private fun item(id: Long, chapterNumber: Double) = ChapterList.Item(
        chapter = Chapter.create().copy(id = id, chapterNumber = chapterNumber),
        downloadState = Download.State.NOT_DOWNLOADED,
    )
}
