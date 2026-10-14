package mihon.desktop.server

import kotlinx.coroutines.runBlocking
import mihon.desktop.source.SourceHttpClient
import mihon.desktop.source.SourceHttpRequest
import mihon.desktop.source.SourceHttpResponse
import mihon.desktop.source.SourceManga
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MangaDexSourcePluginTest {

    @Test
    fun `search chapters and pages map MangaDex responses into desktop models`() = runBlocking {
        val httpClient = QueueHttpClient(
            """
            {
              "data": [{
                "id": "manga-id",
                "attributes": {
                  "title": {"en": "Example Manga"},
                  "description": {"en": "An example description"},
                  "status": "ongoing",
                  "tags": [{"attributes": {"name": {"en": "Fantasy"}}}]
                },
                "relationships": [
                  {"type": "cover_art", "attributes": {"fileName": "cover.jpg"}},
                  {"type": "author", "attributes": {"name": "Example Author"}}
                ]
              }],
              "total": 2
            }
            """.trimIndent(),
            """
            {
              "data": [{
                "id": "chapter-id",
                "attributes": {
                  "chapter": "12.5",
                  "title": "The Return",
                  "readableAt": "2026-10-01T12:00:00+00:00"
                },
                "relationships": [
                  {"type": "scanlation_group", "attributes": {"name": "Example Group"}}
                ]
              }],
              "total": 1
            }
            """.trimIndent(),
            """
            {
              "baseUrl": "https://uploads.example",
              "chapter": {
                "hash": "chapter-hash",
                "data": ["page-1.jpg", "page-2.png"]
              }
            }
            """.trimIndent(),
        )
        val plugin = MangaDexSourcePluginProvider().create(httpClient)

        val search = plugin.search("witch hat", page = 1)
        val manga = search.items.single()
        val chapters = plugin.chapters(manga)
        val pages = plugin.pages(chapters.single())

        assertEquals("mangadex", plugin.descriptor.id)
        assertTrue(search.hasNextPage)
        assertEquals("Example Manga", manga.title)
        assertEquals("Example Author", manga.author)
        assertEquals("An example description", manga.description)
        assertEquals(listOf("Fantasy"), manga.genres)
        assertEquals("ongoing", manga.status)
        assertEquals("https://uploads.mangadex.org/covers/manga-id/cover.jpg.256.jpg", manga.thumbnailUrl)
        assertEquals("Chapter 12.5 — The Return", chapters.single().name)
        assertEquals("Example Group", chapters.single().scanlator)
        assertEquals(2, pages.size)
        assertEquals("https://uploads.example/data/chapter-hash/page-1.jpg", pages[0].imageUrl)
        assertEquals("https://mangadex.org/", pages[0].headers["Referer"])

        assertEquals(3, httpClient.requests.size)
        assertTrue(httpClient.requests[0].url.contains("title=witch+hat"))
        assertTrue(httpClient.requests[0].url.contains("contentRating%5B%5D=safe"))
        assertTrue(httpClient.requests[0].url.contains("contentRating%5B%5D=suggestive"))
        assertTrue(httpClient.requests[1].url.contains("translatedLanguage%5B%5D=en"))
        assertFalse(httpClient.requests.any { it.url.contains("contentRating%5B%5D=erotica") })
    }

    @Test
    fun `search clamps invalid page numbers and reports API errors`() = runBlocking {
        val httpClient = QueueHttpClient("""{"data":[],"total":0}""")
        val plugin = MangaDexSourcePluginProvider().create(httpClient)

        val result = plugin.search("query", page = 0)

        assertTrue(result.items.isEmpty())
        assertFalse(result.hasNextPage)
        assertTrue(httpClient.requests.single().url.contains("offset=0"))

        val failingPlugin = MangaDexSourcePluginProvider().create(
            QueueHttpClient("""{"errors":[]}""", statusCode = 429),
        )
        val failure = runCatching { failingPlugin.search("query", page = 1) }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message.orEmpty().contains("HTTP 429"))
    }

    private class QueueHttpClient(
        vararg bodies: String,
        private val statusCode: Int = 200,
    ) : SourceHttpClient {
        private val responses = ArrayDeque(bodies.toList())
        val requests = mutableListOf<SourceHttpRequest>()

        override suspend fun execute(request: SourceHttpRequest): SourceHttpResponse {
            requests += request
            return SourceHttpResponse(
                statusCode = statusCode,
                headers = emptyMap(),
                body = responses.removeFirst(),
            )
        }
    }
}
