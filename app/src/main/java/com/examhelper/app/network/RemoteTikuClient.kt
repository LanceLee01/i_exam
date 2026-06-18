package com.examhelper.app.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

data class RemoteFile(val name: String, val size: Long)
data class RemoteFileList(val files: List<RemoteFile>)
data class DownloadResult(val success: Boolean, val localPath: String? = null, val error: String? = null)

object RemoteTikuClient {
    private const val TAG = "RemoteTiku"
    // 可通过设置页修改
    var baseUrl = "http://106.14.10.27:14098"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun listFiles(): Result<List<RemoteFile>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("$baseUrl/list").build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext Result.failure(Exception("空响应"))
            val files = com.google.gson.Gson().fromJson(body, RemoteFileList::class.java)
            Result.success(files.files)
        } catch (e: Exception) {
            Log.e(TAG, "listFiles failed", e)
            Result.failure(e)
        }
    }

    suspend fun download(fileName: String, destDir: File, onProgress: (Float) -> Unit = {}): DownloadResult = withContext(Dispatchers.IO) {
        try {
            val encodedName = java.net.URLEncoder.encode(fileName, "UTF-8").replace("+", "%20")
            val request = Request.Builder().url("$baseUrl/download/$encodedName").build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext DownloadResult(false, error = "HTTP ${response.code}")
            val body = response.body ?: return@withContext DownloadResult(false, error = "空响应")
            val total = body.contentLength()
            val tmp = File(destDir, fileName)
            body.byteStream().use { input ->
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var read = 0L
                    var bytes: Int
                    while (input.read(buffer).also { bytes = it } != -1) {
                        output.write(buffer, 0, bytes)
                        read += bytes
                        if (total > 0) onProgress(read.toFloat() / total)
                    }
                }
            }
            DownloadResult(true, tmp.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "download failed: $fileName", e)
            DownloadResult(false, error = e.message)
        }
    }
}
