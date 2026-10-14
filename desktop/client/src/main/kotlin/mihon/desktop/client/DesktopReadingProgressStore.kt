package mihon.desktop.client

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

@Serializable
data class ReadingProgress(
    val sourceId: String,
    val mangaId: String,
    val chapterId: String,
    val pageIndex: Int,
)

class DesktopReadingProgressStore(
    private val file: Path,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun get(sourceId: String, mangaId: String, chapterId: String): Int? {
        return load().firstOrNull {
            it.sourceId == sourceId && it.mangaId == mangaId && it.chapterId == chapterId
        }?.pageIndex
    }

    fun save(progress: ReadingProgress) {
        val updated = load()
            .filterNot {
                it.sourceId == progress.sourceId &&
                    it.mangaId == progress.mangaId &&
                    it.chapterId == progress.chapterId
            } + progress
        Files.createDirectories(file.toAbsolutePath().parent)
        val temporaryFile = file.resolveSibling("${file.fileName}.tmp")
        Files.writeString(temporaryFile, json.encodeToString(updated))
        try {
            Files.move(
                temporaryFile,
                file,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temporaryFile, file, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun load(): List<ReadingProgress> {
        if (!Files.exists(file)) return emptyList()
        return json.decodeFromString(Files.readString(file))
    }
}
