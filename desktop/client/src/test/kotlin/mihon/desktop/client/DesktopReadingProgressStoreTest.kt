package mihon.desktop.client

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class DesktopReadingProgressStoreTest {

    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `reading position is persisted and updated per source manga and chapter`() {
        val store = DesktopReadingProgressStore(temporaryDirectory.resolve("progress.json"))
        val first = ReadingProgress("source-a", "manga-a", "chapter-a", 4)
        store.save(first)
        store.save(first.copy(pageIndex = 9))
        store.save(first.copy(chapterId = "chapter-b", pageIndex = 2))

        assertEquals(9, store.get("source-a", "manga-a", "chapter-a"))
        assertEquals(2, store.get("source-a", "manga-a", "chapter-b"))
        assertEquals(null, store.get("source-b", "manga-a", "chapter-a"))
    }
}
