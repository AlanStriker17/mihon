package mihon.desktop.server

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mihon.desktop.source.SourceChapter
import mihon.desktop.source.SourceDescriptor
import mihon.desktop.source.SourceHttpClient
import mihon.desktop.source.SourceHttpRequest
import mihon.desktop.source.SourceManga
import mihon.desktop.source.SourcePage
import mihon.desktop.source.SourcePageResult
import mihon.desktop.source.SourcePlugin
import mihon.desktop.source.SourcePluginProvider

class MangaDexSourcePluginProvider : SourcePluginProvider {
    override fun create(httpClient: SourceHttpClient): SourcePlugin = MangaDexSourcePlugin(httpClient)
}

private class MangaDexSourcePlugin(
    private val httpClient: SourceHttpClient,
) : SourcePlugin {
    override val descriptor = SourceDescriptor(
        id = "mangadex",
        name = "MangaDex",
        language = "en",
        baseUrl = "https://mangadex.org",
    )

    override suspend fun search(query: String, page: Int): SourcePageResult<SourceManga> {
        val offset = (page.coerceAtLeast(1) - 1) * SEARCH_LIMIT
        val response = get(
            "$API_BASE/manga?limit=$SEARCH_LIMIT&offset=$offset" +
                "&title=${encode(query)}&order%5Brelevance%5D=desc" +
                "&includes%5B%5D=cover_art&contentRating%5B%5D=safe" +
                "&contentRating%5B%5D=suggestive",
        )
        val root = response.jsonObject
        val items = root["data"]?.jsonArray.orEmpty().mapNotNull(::toManga)
        val total = root["total"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: items.size
        return SourcePageResult(items, offset + items.size < total)
    }

    override suspend fun mangaDetails(manga: SourceManga): SourceManga {
        val root = get(
            "$API_BASE/manga/${encodePath(manga.id)}?includes%5B%5D=cover_art" +
                "&includes%5B%5D=author&includes%5B%5D=artist",
        ).jsonObject["data"]?.jsonObject ?: error("MangaDex returned no manga details")
        return toManga(root) ?: error("MangaDex returned invalid manga details for ${manga.id}")
    }

    override suspend fun chapters(manga: SourceManga): List<SourceChapter> {
        val chapters = mutableListOf<SourceChapter>()
        var offset = 0
        var total = Int.MAX_VALUE
        while (offset < total) {
            val response = get(
                "$API_BASE/manga/${encodePath(manga.id)}/feed?limit=$CHAPTER_LIMIT&offset=$offset" +
                    "&translatedLanguage%5B%5D=en&order%5Bchapter%5D=desc" +
                    "&includes%5B%5D=scanlation_group",
            ).jsonObject
            val data = response["data"]?.jsonArray.orEmpty()
            chapters += data.mapNotNull { it.toChapter(manga.id) }
            total = response["total"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                ?: offset + data.size
            if (data.isEmpty()) break
            offset += data.size
        }
        return chapters
    }

    override suspend fun pages(chapter: SourceChapter): List<SourcePage> {
        val root = get("$API_BASE/at-home/server/${encodePath(chapter.id)}").jsonObject
        val baseUrl = root["baseUrl"]?.jsonPrimitive?.contentOrNull
            ?: error("MangaDex returned no image server")
        val chapterData = root["chapter"]?.jsonObject ?: error("MangaDex returned no chapter data")
        val hash = chapterData["hash"]?.jsonPrimitive?.contentOrNull
            ?: error("MangaDex returned no chapter hash")
        val imageFiles = chapterData["data"]?.jsonArray.orEmpty()
        return imageFiles.mapIndexed { index, filename ->
            SourcePage(
                index = index,
                imageUrl = "$baseUrl/data/$hash/${filename.jsonPrimitive.content}",
                headers = mapOf("Referer" to "https://mangadex.org/"),
            )
        }
    }

    private suspend fun get(url: String): JsonElement {
        val response = httpClient.execute(
            SourceHttpRequest(
                method = "GET",
                url = url,
                headers = mapOf("User-Agent" to USER_AGENT),
            ),
        )
        check(response.statusCode in 200..299) {
            "MangaDex returned HTTP ${response.statusCode}: ${response.body.take(MAX_ERROR_LENGTH)}"
        }
        return Json.parseToJsonElement(response.body)
    }

    private fun toManga(element: JsonElement): SourceManga? {
        val manga = element.jsonObject
        val id = manga["id"]?.jsonPrimitive?.contentOrNull ?: return null
        val attributes = manga["attributes"]?.jsonObject ?: return null
        val title = localized(attributes["title"]?.jsonObject) ?: return null
        val relationships = manga["relationships"]?.jsonArray.orEmpty()
        val coverFile = relationships.firstOrNull {
            it.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "cover_art"
        }?.jsonObject?.get("attributes")?.jsonObject?.get("fileName")?.jsonPrimitive?.contentOrNull
        val author = relationships.firstOrNull {
            it.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "author"
        }?.jsonObject?.get("attributes")?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
        val description = localized(attributes["description"]?.jsonObject)
        val genres = attributes["tags"]?.jsonArray.orEmpty().mapNotNull { tag ->
            tag.jsonObject["attributes"]?.jsonObject?.get("name")?.jsonObject?.get("en")
                ?.jsonPrimitive?.contentOrNull
        }.distinct()
        return SourceManga(
            id = id,
            title = title,
            url = "https://mangadex.org/title/$id",
            thumbnailUrl = coverFile?.let { "https://uploads.mangadex.org/covers/$id/$it.256.jpg" },
            author = author,
            description = description,
            genres = genres,
            status = attributes["status"]?.jsonPrimitive?.contentOrNull,
        )
    }

    private fun JsonElement.toChapter(mangaId: String): SourceChapter? {
        val chapter = jsonObject
        val id = chapter["id"]?.jsonPrimitive?.contentOrNull ?: return null
        val attributes = chapter["attributes"]?.jsonObject ?: return null
        val number = attributes["chapter"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
        val title = attributes["title"]?.jsonPrimitive?.contentOrNull
        val name = listOfNotNull(number?.let { "Chapter $it" }, title)
            .joinToString(" — ")
            .ifBlank { "Chapter" }
        val scanlator = chapter["relationships"]?.jsonArray.orEmpty().firstOrNull {
            it.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "scanlation_group"
        }?.jsonObject?.get("attributes")?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
        val uploadedAt = attributes["readableAt"]?.jsonPrimitive?.contentOrNull
            ?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
        return SourceChapter(
            id = id,
            mangaId = mangaId,
            name = name,
            url = "https://mangadex.org/chapter/$id",
            number = number,
            scanlator = scanlator,
            uploadedAtEpochMillis = uploadedAt,
        )
    }

    private fun localized(values: JsonObject?): String? {
        values ?: return null
        return values["en"]?.jsonPrimitive?.contentOrNull
            ?: values.entries.firstOrNull()?.value?.jsonPrimitive?.contentOrNull
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun encodePath(value: String): String = encode(value).replace("+", "%20")

    private companion object {
        const val API_BASE = "https://api.mangadex.org"
        const val SEARCH_LIMIT = 20
        const val CHAPTER_LIMIT = 500
        const val MAX_ERROR_LENGTH = 500
        const val USER_AGENT = "MihonDesktop/0.1 (https://github.com/AlanStriker17/mihon)"
    }
}
