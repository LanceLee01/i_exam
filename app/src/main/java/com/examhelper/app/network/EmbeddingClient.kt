package com.examhelper.app.network

import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

class EmbeddingClient(
    private val apiKey: String,
    private val baseUrl: String = "https://api.siliconflow.cn/v1"
) {

    data class EmbedResult(val values: FloatArray, val dimensions: Int) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is EmbedResult) return false
            return dimensions == other.dimensions && values.contentEquals(other.values)
        }

        override fun hashCode(): Int {
            var result = values.contentHashCode()
            result = 31 * result + dimensions
            return result
        }

        override fun toString(): String {
            return "EmbedResult(values=${values.contentToString().take(100)}..., dims=$dimensions)"
        }
    }

    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request()
            Log.d(TAG, "→ ${req.method} ${req.url} (embedding)")
            val resp = chain.proceed(req)
            Log.d(TAG, "← ${resp.code} ${req.url}")
            resp
        }
        .build()

    suspend fun embed(text: String): EmbedResult? = withContext(Dispatchers.IO) {
        val results = embedBatch(listOf(text))
        results.firstOrNull()
    }

    suspend fun embedBatch(texts: List<String>): List<EmbedResult> = withContext(Dispatchers.IO) {
        val url = "$baseUrl/embeddings"
        val body = EmbeddingRequest(
            model = MODEL,
            input = texts
        )
        val jsonBody = gson.toJson(body)

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            if (!response.isSuccessful) {
                Log.e(TAG, "Embedding API error ${response.code}: $responseBody")
                return@withContext emptyList()
            }
            val embedResponse = gson.fromJson(responseBody, EmbeddingResponse::class.java)
            embedResponse.data?.map { item ->
                val floats = FloatArray(item.embedding.size)
                for (i in item.embedding.indices) {
                    floats[i] = item.embedding[i].toFloat()
                }
                EmbedResult(values = floats, dimensions = floats.size)
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Embedding API failure", e)
            emptyList()
        }
    }

    companion object {
        private const val TAG = "EmbeddingClient"
        private const val MODEL = "BAAI/bge-large-zh-v1.5"

        fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
            val dot = a.foldIndexed(0f) { i, acc, v -> acc + v * b[i] }
            val normA = sqrt(a.fold(0f) { acc, v -> acc + v * v })
            val normB = sqrt(b.fold(0f) { acc, v -> acc + v * v })
            return if (normA == 0f || normB == 0f) 0f else dot / (normA * normB)
        }

        fun topK(
            query: FloatArray,
            candidates: List<Pair<Long, FloatArray>>,
            k: Int
        ): List<Pair<Long, Float>> {
            return candidates
                .map { (id, vec) -> id to cosineSimilarity(query, vec) }
                .sortedByDescending { it.second }
                .take(k)
        }
    }
}

// --- JSON data classes for embedding API ---

data class EmbeddingRequest(
    val model: String,
    val input: List<String>
)

data class EmbeddingResponse(
    @SerializedName("object")
    val obj: String?,
    val data: List<EmbeddingData>?,
    val model: String?
)

data class EmbeddingData(
    @SerializedName("object")
    val obj: String?,
    val index: Int,
    val embedding: List<Double>
)
