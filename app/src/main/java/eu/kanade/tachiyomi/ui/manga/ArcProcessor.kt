package eu.kanade.tachiyomi.ui.manga

object ArcProcessor {

    data class Group<T>(
        val id: String,
        val title: String,
        val chapters: List<T>,
    )

    private val seasonPattern = Regex("""\b(?:season|s)\s*#?\s*(\d+)\b""", RegexOption.IGNORE_CASE)
    private val arcPattern = Regex(
        """\barc\s*[:#-]?\s*([a-z][a-z '\-]{0,31}|#?\d+)""",
        RegexOption.IGNORE_CASE,
    )

    fun <T> group(chapters: List<T>, chapterName: (T) -> String): List<Group<T>> {
        return chapters
            .map { chapter ->
                val name = chapterName(chapter)
                val season = seasonPattern.find(name)?.groupValues?.get(1)
                val arc = arcPattern.find(name)?.groupValues?.get(1)
                    ?.trim(' ', '-', ':', '#')
                    ?.takeIf(String::isNotBlank)
                val title = listOfNotNull(season?.let { "Season $it" }, arc?.let { "Arc $it" })
                    .joinToString(" · ")
                    .ifBlank { "Chapters" }
                val id = listOfNotNull(season?.let { "season:$it" }, arc?.let { "arc:${it.lowercase()}" })
                    .joinToString("|")
                    .ifBlank { "chapters" }
                Triple(chapter, id, title)
            }
            .fold(mutableListOf<Group<T>>()) { groups, (chapter, id, title) ->
                val last = groups.lastOrNull()
                if (last?.id == id) {
                    groups[groups.lastIndex] = last.copy(chapters = last.chapters + chapter)
                } else {
                    groups += Group(id = id, title = title, chapters = listOf(chapter))
                }
                groups
            }
    }
}
