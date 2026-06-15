package com.examhelper.app.network

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class TavilyClient(private val apiKey: String) {

    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun search(
        query: String,
        maxResults: Int = 3,
        includeAnswer: Boolean = true
    ): Result<SearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            val requestBody = mapOf(
                "query" to query,
                "max_results" to maxResults,
                "include_answer" to includeAnswer,
                "search_depth" to "basic"
            )
            val jsonBody = gson.toJson(requestBody)

            val request = Request.Builder()
                .url(SEARCH_URL)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("Tavily API error ${response.code}: ${response.message}")
                }
                val parsed = gson.fromJson(body, TavilyResponse::class.java)
                val references = parsed.results.orEmpty().map { result ->
                    Reference(
                        title = result.title,
                        url = result.url,
                        snippet = result.content
                    )
                }
                SearchResult(
                    answer = parsed.answer,
                    references = references,
                    source = "tavily"
                )
            }
        }
    }

    companion object {
        private const val SEARCH_URL = "https://api.tavily.com/search"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}

data class SearchResult(
    val answer: String?,
    val references: List<Reference>,
    val source: String
)

data class Reference(
    val title: String,
    val url: String,
    val snippet: String
)

data class TavilyResponse(
    val answer: String?,
    val results: List<TavilyResult>?
)

data class TavilyResult(
    val title: String,
    val url: String,
    val content: String,
    val score: Double
)
