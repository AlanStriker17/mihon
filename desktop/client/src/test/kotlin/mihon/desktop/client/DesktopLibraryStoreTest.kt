package mihon.desktop.client

import mihon.desktop.source.SourceManga
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class DesktopLibraryStoreTest {

    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun `library writes are atomic and duplicate entries collapse by source and manga`() {
        val store = DesktopLibraryStore(temporaryDirectory.resolve("library.json"))
        val manga = manga("one")

        store.add(LibraryEntry("source-a", manga))
        val entries = store.add(LibraryEntry("source-a", manga))

        assertEquals(listOf("one"), entries.map { it.manga.id })
        assertEquals(entries, store.load())
    }

    @Test
    fun `the same title remains distinct across sources and can be removed independently`() {
        val store = DesktopLibraryStore(temporaryDirectory.resolve("library.json"))
        store.replace(
            listOf(
                LibraryEntry("source-a", manga("one")),
                LibraryEntry("source-b", manga("one")),
            ),
        )

        val remaining = store.remove("source-a", "one")

        assertEquals(listOf("source-b"), remaining.map { it.sourceId })
    }

    @Test
    fun `backup export and import merge entries without duplicates`() {
        val localStore = DesktopLibraryStore(temporaryDirectory.resolve("local.json"))
        localStore.add(LibraryEntry("source-a", manga("local")))
        val backup = temporaryDirectory.resolve("backup.json")
        localStore.exportBackup(backup)

        val restoredStore = DesktopLibraryStore(temporaryDirectory.resolve("restored.json"))
        restoredStore.add(LibraryEntry("source-a", manga("local")))
        restoredStore.mergeBackup(backup)

        assertEquals(listOf("local"), restoredStore.load().map { it.manga.id })
    }

    private fun manga(id: String) = SourceManga(
        id = id,
        title = "Title $id",
        url = "/$id",
    )
}
