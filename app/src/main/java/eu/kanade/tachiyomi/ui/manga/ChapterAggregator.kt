package eu.kanade.tachiyomi.ui.manga

object ChapterAggregator {

    sealed interface Row {
        data class Chapters(val items: List<ChapterList.Item>) : Row
        data class MissingCount(val item: ChapterList.MissingCount) : Row
    }

    fun aggregate(items: List<ChapterList>): List<Row> {
        val result = mutableListOf<Row>()
        var pending = mutableListOf<ChapterList.Item>()
        var pendingNumber: Double? = null

        fun flush() {
            if (pending.isNotEmpty()) {
                result += Row.Chapters(pending.toList())
                pending = mutableListOf()
            }
            pendingNumber = null
        }

        items.forEach { item ->
            when (item) {
                is ChapterList.MissingCount -> {
                    flush()
                    result += Row.MissingCount(item)
                }
                is ChapterList.Item -> {
                    val number = item.chapter.chapterNumber.takeIf { it.isFinite() && it >= 0 }
                    if (number == null || (pendingNumber != null && pendingNumber != number)) flush()
                    if (number == null) {
                        result += Row.Chapters(listOf(item))
                    } else {
                        if (pending.isEmpty()) pendingNumber = number
                        pending += item
                    }
                }
            }
        }
        flush()
        return result
    }
}
