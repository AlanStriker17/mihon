package eu.kanade.tachiyomi.ui.manga

object ReadingTimeEstimator {
    private const val DEFAULT_MINUTES = 4

    fun estimateMinutes(pageCount: Int? = null): Int {
        if (pageCount == null || pageCount <= 0) return DEFAULT_MINUTES
        return ((pageCount * 10) / 60).coerceAtLeast(1)
    }
}
