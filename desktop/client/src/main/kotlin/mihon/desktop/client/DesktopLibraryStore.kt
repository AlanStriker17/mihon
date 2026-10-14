package mihon.desktop.client

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mihon.desktop.source.SourceManga
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

@Serializable
data class LibraryEntry(
    val sourceId: String,
    val manga: SourceManga,
    val addedAtEpochMillis: Long = System.currentTimeMillis(),
)

class DesktopLibraryStore(
    private val libraryFile: Path,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun load(): List<LibraryEntry> {
        if (!Files.exists(libraryFile)) return emptyList()
        return json.decodeFromString(Files.readString(libraryFile))
    }

    fun add(entry: LibraryEntry): List<LibraryEntry> = save(load() + entry)

    fun remove(sourceId: String, mangaId: String): List<LibraryEntry> = save(
        load().filterNot { it.sourceId == sourceId && it.manga.id == mangaId },
    )

    fun replace(entries: List<LibraryEntry>): List<LibraryEntry> = save(entries)

    fun mergeBackup(backup: Path): List<LibraryEntry> {
        val existing = load()
        val imported = json.decodeFromString<List<LibraryEntry>>(Files.readString(backup))
        return save(existing + imported)
    }

    fun exportBackup(destination: Path) {
        Files.createDirectories(destination.toAbsolutePath().parent)
        atomicWrite(destination, json.encodeToString(load()))
    }

    private fun save(entries: List<LibraryEntry>): List<LibraryEntry> {
        val unique = entries.distinctBy { it.sourceId to it.manga.id }
        Files.createDirectories(libraryFile.toAbsolutePath().parent)
        atomicWrite(libraryFile, json.encodeToString(unique))
        return unique
    }

    private fun atomicWrite(destination: Path, contents: String) {
        val temporaryFile = destination.resolveSibling("${destination.fileName}.tmp")
        Files.writeString(temporaryFile, contents)
        try {
            Files.move(
                temporaryFile,
                destination,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temporaryFile, destination, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
