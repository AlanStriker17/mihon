package mihon.desktop.server

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.runBlocking
import mihon.desktop.source.SourceDescriptor
import mihon.desktop.source.SourceHttpClient
import mihon.desktop.source.SourceHttpRequest
import mihon.desktop.source.SourceHttpResponse
import mihon.desktop.source.SourceManga
import mihon.desktop.source.SourceChapter
import mihon.desktop.source.SourcePlugin
import mihon.desktop.source.SourcePluginProvider
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.ServiceLoader
import java.util.ServiceConfigurationError
import java.util.concurrent.CountDownLatch
import java.net.URLClassLoader
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

private val json = Json {
    encodeDefaults = true
    explicitNulls = false
}

fun startSourceServer(port: Int = configuredPort()): HttpServer {
    val client = JdkSourceHttpClient()
    val plugins = loadPlugins(client)
    val descriptors = plugins.map { it.descriptor }

    val server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
    server.createContext("/health") { exchange ->
        exchange.respond(200, """{"status":"ok"}""")
    }
    server.createContext("/build") { exchange ->
        if (exchange.requestMethod != "GET") {
            exchange.respond(405, """{"error":"method not allowed"}""")
        } else {
            val page = Thread.currentThread().contextClassLoader
                .getResourceAsStream("web/build.html")
                ?.use { it.readBytes().decodeToString() }
                ?: error("Windows build page resource is missing")
            exchange.respond(200, page, "text/html; charset=utf-8")
        }
    }
    server.createContext("/api/sources") { exchange ->
        try {
            val path = exchange.requestURI.path.split('/').filter(String::isNotEmpty)
            if (path.size == 2 && exchange.requestMethod == "GET") {
                exchange.respond(200, json.encodeToString<List<SourceDescriptor>>(descriptors))
            } else if (path.size < 4) {
                exchange.respond(404, """{"error":"not found"}""")
            } else {
                val sourceId = decode(path[2])
                val plugin = plugins.firstOrNull { it.descriptor.id == sourceId }
                if (plugin == null) {
                    exchange.respond(404, """{"error":"source not found"}""")
                } else {
                    when {
                        path.size == 4 && path[3] == "search" && exchange.requestMethod == "GET" -> {
                            val query = exchange.requestURI.rawQuery.orEmpty().split('&')
                                .mapNotNull { part ->
                                    val pair = part.split('=', limit = 2)
                                    pair.firstOrNull()?.let { decode(it) to decode(pair.getOrElse(1) { "" }) }
                                }.toMap()
                            val page = query["page"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                            val result = runBlocking { plugin.search(query["q"].orEmpty(), page) }
                            exchange.respond(200, json.encodeToString(result))
                        }
                        path.size == 4 && path[3] == "details" && exchange.requestMethod == "POST" -> {
                            val manga = json.decodeFromString<SourceManga>(exchange.requestBody.readBytes().decodeToString())
                            exchange.respond(200, json.encodeToString(runBlocking { plugin.mangaDetails(manga) }))
                        }
                        path.size == 4 && path[3] == "chapters" && exchange.requestMethod == "POST" -> {
                            val manga = json.decodeFromString<SourceManga>(exchange.requestBody.readBytes().decodeToString())
                            exchange.respond(200, json.encodeToString(runBlocking { plugin.chapters(manga) }))
                        }
                        path.size == 4 && path[3] == "pages" && exchange.requestMethod == "POST" -> {
                            val chapter = json.decodeFromString<SourceChapter>(exchange.requestBody.readBytes().decodeToString())
                            exchange.respond(200, json.encodeToString(runBlocking { plugin.pages(chapter) }))
                        }
                        else -> exchange.respond(405, """{"error":"method not allowed"}""")
                    }
                }
            }
        } catch (error: Exception) {
            exchange.respond(502, json.encodeToString(mapOf("error" to (error.message ?: "source request failed"))))
        }
    }
    server.executor = Executors.newFixedThreadPool(4) { runnable ->
        Thread(runnable, "mihon-source-api").apply { isDaemon = true }
    }
    server.start()
    println("Mihon source server listening on http://127.0.0.1:$port")
    return server
}

private fun loadPlugins(client: SourceHttpClient): List<SourcePlugin> {
    val loaded = buildList {
        add(MangaDexSourcePluginProvider().create(client))
        ServiceLoader.load(SourcePluginProvider::class.java).forEach { provider ->
            try {
                add(provider.create(client))
            } catch (error: Exception) {
                System.err.println("Unable to create bundled source plugin ${provider.javaClass.name}: ${error.message}")
            }
        }

        val pluginDirectory = Path.of(System.getProperty("user.home"), ".mihon", "plugins")
        Files.createDirectories(pluginDirectory)
        Files.list(pluginDirectory).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".jar", ignoreCase = true) }
                .sorted()
                .forEach { path ->
                    val loader = URLClassLoader(
                        arrayOf(path.toUri().toURL()),
                        SourcePluginProvider::class.java.classLoader,
                    )
                    try {
                        val providers = try {
                            ServiceLoader.load(SourcePluginProvider::class.java, loader).toList()
                        } catch (error: ServiceConfigurationError) {
                            System.err.println("Unable to load source plugin ${path.fileName}: ${error.message}")
                            emptyList()
                        }
                        providers.forEach { provider ->
                            try {
                                add(provider.create(client))
                            } catch (error: Exception) {
                                System.err.println(
                                    "Unable to create source plugin ${provider.javaClass.name} from ${path.fileName}: ${error.message}",
                                )
                            }
                        }
                        if (providers.isEmpty()) {
                            loader.close()
                        } else {
                            Runtime.getRuntime().addShutdownHook(Thread {
                                try {
                                    loader.close()
                                } catch (error: Exception) {
                                    System.err.println("Unable to close source plugin ${path.fileName}: ${error.message}")
                                }
                            })
                        }
                    } catch (error: Exception) {
                        loader.close()
                        System.err.println("Unable to load source plugin ${path.fileName}: ${error.message}")
                    }
                }
        }
    }
    return loaded.groupBy { it.descriptor.id }
        .onEach { (id, duplicates) ->
            if (duplicates.size > 1) {
                System.err.println("Duplicate source plugin id '$id'; only the first plugin will be used")
            }
        }
        .values
        .map { it.first() }
        .sortedBy { it.descriptor.name.lowercase() }
}

fun main() {
    startSourceServer()
    CountDownLatch(1).await()
}

private fun configuredPort(): Int = System.getenv("MIHON_SOURCE_SERVER_PORT")
    ?.toIntOrNull()
    ?.takeIf { it in 1..65535 }
    ?: DEFAULT_PORT

private fun decode(value: String): String = URLDecoder.decode(value, StandardCharsets.UTF_8)

private fun HttpExchange.respond(
    statusCode: Int,
    body: String,
    contentType: String = "application/json; charset=utf-8",
) {
    val bytes = body.toByteArray(Charsets.UTF_8)
    responseHeaders.set("Content-Type", contentType)
    responseHeaders.set("Cache-Control", "no-store")
    sendResponseHeaders(statusCode, bytes.size.toLong())
    responseBody.use { it.write(bytes) }
}

private class JdkSourceHttpClient : SourceHttpClient {
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    override suspend fun execute(request: SourceHttpRequest): SourceHttpResponse {
        val builder = HttpRequest.newBuilder(URI.create(request.url))
            .timeout(Duration.ofSeconds(30))
        request.headers.forEach(builder::header)
        builder.method(
            request.method.uppercase(),
            request.body?.let(HttpRequest.BodyPublishers::ofString)
                ?: HttpRequest.BodyPublishers.noBody(),
        )
        val response = client.sendCancellable(
            builder.build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        return SourceHttpResponse(
            statusCode = response.statusCode(),
            headers = response.headers().map(),
            body = response.body(),
        )
    }
}

private suspend fun <T> HttpClient.sendCancellable(
    request: HttpRequest,
    bodyHandler: HttpResponse.BodyHandler<T>,
): HttpResponse<T> = suspendCancellableCoroutine { continuation ->
    val future = sendAsync(request, bodyHandler)
    continuation.invokeOnCancellation { future.cancel(true) }
    future.whenComplete { response, error ->
        if (error != null) {
            continuation.resumeWithException(error)
        } else if (continuation.isActive) {
            continuation.resume(response)
        }
    }
}

private const val DEFAULT_PORT = 8766
