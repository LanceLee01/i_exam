package com.examhelper.app.network

import android.util.Log
import com.examhelper.app.util.ExtractedTextBus
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class LLMClient {

    private val gson = Gson()
    val reasoningBuffer = StringBuilder()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request()
            Log.d(TAG, "→ ${req.method} ${req.url}")
            val resp = chain.proceed(req)
            Log.d(TAG, "← ${resp.code} ${req.url}")
            resp
        }
        .build()

    sealed class Result {
        data class Success(val content: String) : Result()
        data class Error(val code: Int, val message: String) : Result()
        data object NetworkError : Result()
    }

    private fun buildUrl(endpoint: String): String {
        val base = endpoint.trimEnd('/')
        return when {
            base.contains("/chat/completions") -> base
            base.endsWith("/v1") -> "$base/chat/completions"
            else -> "$base/v1/chat/completions"
        }
    }

    private fun buildRequest(
        endpoint: String,
        apiKey: String,
        model: String,
        temperature: Float,
        maxTokens: Int,
        systemPrompt: String,
        userMessage: String,
        stream: Boolean
    ): Request {
        val requestBody = ChatRequest(
            model = model,
            messages = listOf(
                Message("system", systemPrompt),
                Message("user", userMessage)
            ),
            temperature = temperature,
            maxTokens = maxTokens,
            stream = stream
        )

        val jsonBody = gson.toJson(requestBody)
        val url = buildUrl(endpoint)

        return Request.Builder()
            .url(url)
            .apply {
                if (apiKey.isNotBlank()) {
                    addHeader("Authorization", "Bearer $apiKey")
                }
            }
            .addHeader("Content-Type", "application/json")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()
    }

    suspend fun chatSync(
        endpoint: String,
        apiKey: String,
        model: String,
        temperature: Float,
        maxTokens: Int,
        systemPrompt: String,
        userMessage: String
    ): Result = suspendCancellableCoroutine { cont ->
        val request = buildRequest(endpoint, apiKey, model, temperature, maxTokens, systemPrompt, userMessage, false)

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Network error", e)
                cont.resume(Result.NetworkError)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string()
                    Log.d(TAG, "Response body: ${body?.take(1000)}")
                    if (!response.isSuccessful) {
                        val errorMsg = try {
                            gson.fromJson(body, ChatErrorResponse::class.java)
                                ?.error?.message ?: response.message
                        } catch (_: Exception) {
                            body ?: response.message
                        }
                        cont.resume(Result.Error(response.code, errorMsg))
                        return
                    }
                    val chatResponse = gson.fromJson(body, ChatResponse::class.java)
                    val msg = chatResponse.choices.firstOrNull()?.message
                    val content = when {
                        !msg?.content.isNullOrBlank() -> msg!!.content
                        !msg?.reasoningContent.isNullOrBlank() -> msg!!.reasoningContent!!
                        else -> ""
                    }
                    Log.d(TAG, "Extracted content length: ${content.length}")
                    cont.resume(Result.Success(content))
                } catch (e: Exception) {
                    Log.e(TAG, "Parse error", e)
                    cont.resume(Result.Error(-1, "解析响应失败: ${e.message}"))
                }
            }
        })
    }

    suspend fun getPromptTokens(
        endpoint: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        userMessage: String
    ): Int = suspendCancellableCoroutine { cont ->
        val rawText = "<|im_start|>system\n$systemPrompt<|im_end|>\n<|im_start|>user\n$userMessage<|im_end|>\n<|im_start|>assistant\n"
        val tokenizeBody = """{"content":${gson.toJson(rawText)}}"""
        val baseUrl = buildUrl(endpoint).replace("/chat/completions", "")
        val url = "$baseUrl/tokenize"

        val request = Request.Builder()
            .url(url)
            .apply {
                if (apiKey.isNotBlank()) addHeader("Authorization", "Bearer $apiKey")
            }
            .addHeader("Content-Type", "application/json")
            .post(tokenizeBody.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w(TAG, "tokenize failed, falling back", e)
                cont.resume(0)
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: ""
                    val json = gson.fromJson(body, Map::class.java)
                    val len = (json["length"] as? Double)?.toInt()
                        ?: (json["tokens"] as? List<*>)?.size ?: 0
                    Log.d(TAG, "Tokenized: $len tokens")
                    cont.resume(len)
                } catch (e: Exception) {
                    Log.w(TAG, "tokenize parse failed", e)
                    cont.resume(0)
                }
            }
        })
    }

    fun chatStream(
        endpoint: String,
        apiKey: String,
        model: String,
        temperature: Float,
        maxTokens: Int,
        systemPrompt: String,
        userMessage: String
    ): Flow<String> = callbackFlow {
        reasoningBuffer.clear()
        val request = buildRequest(endpoint, apiKey, model, temperature, maxTokens, systemPrompt, userMessage, true)

        val factory = EventSources.createFactory(client)

        val eventSource = factory.newEventSource(request, object : EventSourceListener() {
            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String
            ) {
                if (data == "[DONE]") {
                    close()
                    return
                }
                try {
                    val chunk = gson.fromJson(data, ChatStreamChunk::class.java)
                    if (chunk.usage != null) {
                        ExtractedTextBus.lastPromptTokens = chunk.usage.promptTokens
                        Log.d(TAG, "Usage: prompt=${chunk.usage.promptTokens} completion=${chunk.usage.completionTokens} total=${chunk.usage.totalTokens}")
                    }
                    val delta = chunk.choices.firstOrNull()?.delta
                    val text = delta?.content ?: ""
                    if (text.isNotEmpty()) {
                        trySend(text)
                    }
                    val reasoning = delta?.reasoningContent ?: ""
                    if (reasoning.isNotEmpty()) {
                        reasoningBuffer.append(reasoning)
                    }
                } catch (_: Exception) {
                    // skip
                }
            }

            override fun onFailure(
                eventSource: EventSource,
                t: Throwable?,
                response: Response?
            ) {
                close(t ?: IOException("Stream failed: ${response?.code}"))
            }

            override fun onClosed(eventSource: EventSource) {
                close()
            }
        })

        awaitClose { eventSource.cancel() }
    }.flowOn(Dispatchers.IO)

    companion object {
        private const val TAG = "LLMClient"
    }
}

// --- JSON data classes for OpenAI API ---

data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val temperature: Float? = null,
    @SerializedName("max_tokens")
    val maxTokens: Int? = null,
    val stream: Boolean = false
)

data class Message(
    val role: String,
    val content: String,
    @SerializedName("reasoning_content")
    val reasoningContent: String? = null
)

data class ChatResponse(
    val id: String?,
    @SerializedName("object")
    val obj: String?,
    val created: Long?,
    val model: String?,
    val choices: List<Choice>,
    val usage: Usage?
)

data class Choice(
    val index: Int,
    val message: Message?,
    @SerializedName("finish_reason")
    val finishReason: String?
)

data class Usage(
    @SerializedName("prompt_tokens")
    val promptTokens: Int,
    @SerializedName("completion_tokens")
    val completionTokens: Int,
    @SerializedName("total_tokens")
    val totalTokens: Int
)

data class ChatErrorResponse(
    val error: ErrorDetail?
)

data class ErrorDetail(
    val message: String,
    val type: String?,
    val code: String?
)

data class ChatStreamChunk(
    val choices: List<StreamChoice>,
    val usage: Usage? = null
)

data class StreamChoice(
    val index: Int,
    val delta: DeltaContent?,
    @SerializedName("finish_reason")
    val finishReason: String?
)

data class DeltaContent(
    val role: String?,
    val content: String?,
    @SerializedName("reasoning_content")
    val reasoningContent: String? = null
)
