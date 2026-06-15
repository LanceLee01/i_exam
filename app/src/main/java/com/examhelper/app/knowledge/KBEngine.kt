package com.examhelper.app.knowledge

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.examhelper.app.ExamApplication
import com.examhelper.app.knowledge.db.AppDatabase
import com.examhelper.app.knowledge.db.SourceFile
import com.examhelper.app.knowledge.db.WikiPage
import com.examhelper.app.knowledge.db.Wikilink
import com.examhelper.app.network.LLMClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID

data class ImportResult(
    val success: Boolean,
    val pagesGenerated: Int = 0,
    val error: String? = null,
    val skipped: Boolean = false
)

data class SearchResult(
    val pages: List<WikiPage>,
    val ftsPages: List<WikiPage> = emptyList(),
    val trigramPages: List<WikiPage> = emptyList()
)

class KBEngine(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val llmClient = LLMClient()

    fun createImportIntent(): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/plain", "text/markdown", "text/x-markdown"))
        }
    }

    suspend fun importFile(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        try {
            val content = readFileContent(uri) ?: return@withContext ImportResult(false, error = "无法读取文件内容")
            if (content.isBlank()) return@withContext ImportResult(false, error = "文件内容为空")

            val hash = computeSHA256(content)
            val path = uri.toString()
            val existing = db.sourceFileDao().getByPath(path)
            if (existing?.contentHash == hash) {
                Log.d(TAG, "SHA256 match, skipping reimport: $path")
                return@withContext ImportResult(true, skipped = true)
            }

            val fileName = getFileName(uri)
            val sourceId = existing?.id ?: UUID.randomUUID().toString()

            val config = ExamApplication.instance.appConfig.getSnapshot()
            val systemPrompt = buildWikiPrompt()
            val truncated = if (content.length > MAX_DOC_CHARS) {
                content.take(MAX_DOC_CHARS) + "\n\n[文档过长，已截断]"
            } else content

            val result = llmClient.chatSync(
                endpoint = config.apiEndpoint,
                apiKey = config.apiKey,
                model = config.modelName,
                temperature = 0.3f,
                maxTokens = config.maxTokens,
                systemPrompt = systemPrompt,
                userMessage = truncated
            )

            val pages = when (result) {
                is LLMClient.Result.Success -> {
                    Log.d(TAG, "LLM response received (${result.content.length} chars)")
                    parseWikiPages(result.content, sourceId)
                }
                is LLMClient.Result.Error -> {
                    Log.e(TAG, "LLM error: ${result.message}")
                    return@withContext ImportResult(false, error = "LLM 出错: ${result.message}")
                }
                is LLMClient.Result.NetworkError -> {
                    Log.e(TAG, "Network error")
                    return@withContext ImportResult(false, error = "网络连接失败")
                }
            }

            if (pages.isEmpty()) {
                return@withContext ImportResult(false, error = "LLM 未生成任何知识页，请检查文档内容")
            }

            db.wikiPageDao().insertAll(pages)
            Log.d(TAG, "Inserted ${pages.size} wiki pages")

            val sourceFile = SourceFile(
                id = sourceId,
                filePath = path,
                fileName = fileName,
                fileType = getFileType(uri),
                contentHash = hash,
                pageCount = pages.size
            )
            db.sourceFileDao().insert(sourceFile)

            val allTitles = pages.map { it.uid to it.title }.toMap()
            val links = mutableListOf<Wikilink>()
            for (page in pages) {
                val refs = extractWikilinks(page.content)
                for ((label, targetTitle) in refs) {
                    val targetPage = pages.firstOrNull { it.title == targetTitle }
                    if (targetPage != null) {
                        links.add(Wikilink(
                            sourceId = page.id,
                            targetId = targetPage.id,
                            label = label
                        ))
                    }
                }
            }
            if (links.isNotEmpty()) {
                db.wikilinkDao().insertAll(links)
                Log.d(TAG, "Inserted ${links.size} wikilinks")
            }

            ImportResult(success = true, pagesGenerated = pages.size)
        } catch (e: Exception) {
            Log.e(TAG, "importFile failed", e)
            ImportResult(false, error = e.message ?: "未知错误")
        }
    }

    suspend fun searchByQuestion(questionText: String): SearchResult {
        val ftsResults = try {
            val query = buildFtsQuery(questionText)
            db.wikiPageDao().searchFts(query, 10)
        } catch (e: Exception) {
            Log.w(TAG, "FTS search failed: ${e.message}")
            emptyList()
        }

        val allPages = db.wikiPageDao().getAll()
        val qTrigrams = KBEntry.computeTrigrams(questionText)

        val trigramResults = allPages
            .map { page ->
                val pageTriContent = KBEntry.computeTrigrams(page.title + page.summary + page.content)
                page to KBEntry.jaccard(qTrigrams, pageTriContent)
            }
            .filter { it.second > 0.1f }
            .sortedByDescending { it.second }
            .take(10)
            .map { it.first }

        return SearchResult(
            pages = (ftsResults + trigramResults).distinctBy { it.id },
            ftsPages = ftsResults,
            trigramPages = trigramResults
        )
    }

    suspend fun canAnswerFromKB(questionText: String, pages: List<WikiPage>): Boolean {
        return pages.isNotEmpty()
    }

    suspend fun getAnswerFromKB(questionText: String, pages: List<WikiPage>): String? {
        if (pages.isEmpty()) return null
        return pages.joinToString("\n\n") { page ->
            "【${page.title}】\n${page.summary}\n${page.content.take(500)}"
        }
    }

    suspend fun getPageCount(): Int = db.wikiPageDao().getCount()

    suspend fun getPage(uid: String): WikiPage? = db.wikiPageDao().getByUid(uid)

    suspend fun getAllPages(): List<WikiPage> = db.wikiPageDao().getAll()

    suspend fun getWikilinks(pageId: Long): List<Wikilink> = db.wikilinkDao().getAllForPage(pageId)

    suspend fun clearAll() {
        db.wikilinkDao().clearAll()
        db.wikiPageDao().clearAll()
        db.sourceFileDao().clearAll()
    }

    suspend fun deletePage(uid: String) {
        val page = db.wikiPageDao().getByUid(uid) ?: return
        db.wikilinkDao().deleteAllForPage(page.id)
        db.wikiPageDao().delete(page)
    }

    private suspend fun readFileContent(uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.bufferedReader().use { it?.readText() }
        } catch (e: Exception) {
            Log.e(TAG, "readFileContent failed", e)
            null
        }
    }

    private fun getFileName(uri: Uri): String {
        return try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val idx = it.getColumnIndex("_display_name")
                    if (idx >= 0) it.getString(idx) else uri.lastPathSegment ?: "未命名"
                } else uri.lastPathSegment ?: "未命名"
            } ?: (uri.lastPathSegment ?: "未命名")
        } catch (_: Exception) {
            uri.lastPathSegment ?: "未命名"
        }
    }

    private fun getFileType(uri: Uri): String {
        val name = getFileName(uri).lowercase()
        return when {
            name.endsWith(".md") || name.endsWith(".markdown") -> "md"
            else -> "txt"
        }
    }

    private fun computeSHA256(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(content.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun buildWikiPrompt(): String {
        return """
你是一个知识库构建助手。请分析以下资料，提取核心知识点，为每个知识点生成一个 Wiki 页面。

每个页面使用以下格式（用 --- 分隔不同页面）：

---
type: concept|entity|procedure|definition
title: 知识点标题（简洁明了）
tags: 标签1, 标签2, 标签3
summary: 一句话概述
---

## 概述
一句话摘要

## 详细内容
详细解释知识点的内容...

## 相关概念
- [[概念名称]] — 关系说明

要求：
1. 每个独立的知识点生成一个页面
2. type 根据内容选择: concept(概念) / entity(实体) / procedure(流程/步骤) / definition(定义)
3. tags 使用 3-5 个中文标签，逗号分隔
4. 使用 [[概念名称]] 标注与其他知识点的关联
5. 用中文输出
6. 如果资料内容较少，生成 1-3 个页面即可
        """.trimIndent()
    }

    private fun parseWikiPages(text: String, sourceId: String): List<WikiPage> {
        val pages = mutableListOf<WikiPage>()

        val blocks = text.split(Regex("---+\n?")).filter { it.isNotBlank() }

        for (block in blocks) {
            val trimmed = block.trim()
            if (trimmed.isEmpty()) continue

            val frontmatter = mutableMapOf<String, String>()
            val contentStart = extractFrontmatter(trimmed, frontmatter)

            val title = frontmatter["title"]
            if (title.isNullOrBlank()) continue

            val pageType = frontmatter["type"] ?: "concept"
            val tags = frontmatter["tags"] ?: ""
            val summary = frontmatter["summary"] ?: ""

            val content = if (contentStart < trimmed.length) {
                trimmed.substring(contentStart).trim()
            } else ""

            pages.add(
                WikiPage(
                    title = title,
                    content = content,
                    summary = summary,
                    pageType = pageType,
                    tags = tags,
                    sources = sourceId
                )
            )
        }

        Log.d(TAG, "Parsed ${pages.size} wiki pages from LLM response")
        return pages
    }

    private fun extractFrontmatter(text: String, out: MutableMap<String, String>): Int {
        // Find first --- delimiter
        val firstDelim = text.indexOf("---")
        if (firstDelim < 0) return 0

        // Find second --- delimiter (after the first)
        val secondDelim = text.indexOf("---", firstDelim + 3)
        if (secondDelim < 0) return 0

        // Parse YAML key-value pairs between the delimiters
        val fmBlock = text.substring(firstDelim + 3, secondDelim).trim()
        for (line in fmBlock.lines()) {
            val trimmed = line.trim()
            val colonIdx = trimmed.indexOf(':')
            if (colonIdx > 0) {
                val key = trimmed.substring(0, colonIdx).trim().lowercase()
                val value = trimmed.substring(colonIdx + 1).trim()
                if (key in setOf("type", "title", "tags", "summary")) {
                    out[key] = value
                }
            }
        }

        // Return position after the second --- and its trailing newline
        var pos = secondDelim + 3
        while (pos < text.length && text[pos] == '\n') pos++
        return pos
    }

    private fun extractWikilinks(content: String): List<Pair<String, String>> {
        val regex = Regex("""\[\[(.+?)\]\]""")
        return regex.findAll(content).map { match ->
            val full = match.groupValues[1]
            val parts = full.split("|", "—", "——", "-").map { it.trim() }
            val label = if (parts.size >= 2) parts[1] else full
            val target = parts[0]
            label to target
        }.toList()
    }

    private fun buildFtsQuery(text: String): String {
        val cleaned = text
            .replace(Regex("""[.,，。、；;：:！!？?（）()\[\]【】《》'"“”\s]+"""), " ")
            .trim()
        if (cleaned.isBlank()) return "*"
        return cleaned.split(" ")
            .filter { it.length >= 2 }
            .take(10)
            .joinToString(" OR ") { "$it*" }
    }

    companion object {
        private const val TAG = "KBEngine"
        private const val MAX_DOC_CHARS = 8000
    }
}
