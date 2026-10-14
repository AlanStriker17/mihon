package mihon.desktop.source

interface SourceHttpClient {
    suspend fun execute(request: SourceHttpRequest): SourceHttpResponse
}

data class SourceHttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
)

data class SourceHttpResponse(
    val statusCode: Int,
    val headers: Map<String, List<String>>,
    val body: String,
)
