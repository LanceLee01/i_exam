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

data class LlmAnswer(
    val question: String,
    val answer: String,
    val references: List<Pair<String, String>>  // (pageUid, pageTitle)
)

class KBEngine(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val llmClient = LLMClient()

    fun createImportIntent(): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "text/plain", "text/markdown", "text/x-markdown",
                "application/pdf",
                "application/vnd.ms-powerpoint", "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            ))
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

            // Fallback: if parser produced 0 pages, use full response as a single page
            val finalPages = if (pages.isEmpty()) {
                Log.w(TAG, "Parser returned 0 pages, creating fallback page from raw response")
                listOf(WikiPage(
                    title = fileName.removeSuffix(".pptx").removeSuffix(".ppt").removeSuffix(".pdf")
                        .removeSuffix(".xlsx").removeSuffix(".xls").removeSuffix(".txt").removeSuffix(".md"),
                    content = result.content.take(4000),
                    summary = result.content.lines().firstOrNull()?.take(200) ?: "知识页面",
                    pageType = "concept",
                    tags = "知识, 文档",
                    sources = sourceId
                ))
            } else pages

            db.wikiPageDao().insertAll(finalPages)
            Log.d(TAG, "Inserted ${finalPages.size} wiki pages")

            val sourceFile = SourceFile(
                id = sourceId,
                filePath = path,
                fileName = fileName,
                fileType = getFileType(uri),
                contentHash = hash,
                pageCount = finalPages.size
            )
            db.sourceFileDao().insert(sourceFile)

            val allTitles = finalPages.map { it.uid to it.title }.toMap()
            val links = mutableListOf<Wikilink>()
            for (page in finalPages) {
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

            ImportResult(success = true, pagesGenerated = finalPages.size)
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

    /** LLM-powered natural language Q&A over wiki knowledge base */
    suspend fun answerQuestion(question: String): LlmAnswer {
        // Step 1: Hybrid search for relevant pages
        val searchResult = searchByQuestion(question)
        val topPages = searchResult.pages.take(5)
        if (topPages.isEmpty()) return LlmAnswer("", "知识库中没有相关内容", emptyList())

        // Step 2: Build context from top pages
        val context = topPages.joinToString("\n\n---\n\n") { page ->
            "【标题】${page.title}\n【类型】${page.pageType}\n【标签】${page.tags}\n【摘要】${page.summary}\n【正文】${page.content.take(800)}"
        }

        // Step 3: Call LLM
        val config = ExamApplication.instance.appConfig.getSnapshot()
        val systemPrompt = """你是知识库问答助手。根据提供的知识库页面内容回答问题。
如果答案在提供的页面中找不到，如实说"知识库中暂无相关信息"。
回答时引用具体页面标题。格式：先给出简洁答案，然后列出参考页面。"""

        val userMessage = "知识库内容：\n$context\n\n问题：$question\n\n请根据上述知识库内容回答问题，并列出参考的页面标题。"

        val result = llmClient.chatSync(
            endpoint = config.apiEndpoint,
            apiKey = config.apiKey,
            model = config.modelName,
            temperature = 0.3f,
            maxTokens = 1024,
            systemPrompt = systemPrompt,
            userMessage = userMessage
        )

        return when (result) {
            is LLMClient.Result.Success -> {
                Log.d(TAG, "LLM answer received (${result.content.length} chars)")
                LlmAnswer(
                    question = question,
                    answer = result.content,
                    references = topPages.map { it.uid to it.title }
                )
            }
            is LLMClient.Result.Error -> LlmAnswer(question, "问答出错: ${result.message}", emptyList())
            is LLMClient.Result.NetworkError -> LlmAnswer(question, "网络连接失败", emptyList())
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

    suspend fun getSourceFileCount(): Int = db.sourceFileDao().getAll().size

    suspend fun getPageById(id: Long): WikiPage? = db.wikiPageDao().getById(id)

    suspend fun deletePage(uid: String) {
        val page = db.wikiPageDao().getByUid(uid) ?: return
        db.wikilinkDao().deleteAllForPage(page.id)
        db.wikiPageDao().delete(page)
    }

    private suspend fun readFileContent(uri: Uri): String? {
        val name = getFileName(uri).lowercase()
        return try {
            when {
                name.endsWith(".pdf") -> readPdfContent(uri)
                name.endsWith(".pptx") -> readPptxContent(uri)
                name.endsWith(".ppt") -> readPptContent(uri)
                name.endsWith(".xlsx") || name.endsWith(".xls") || name.endsWith(".et") -> readExcelContent(uri)
                else -> context.contentResolver.openInputStream(uri)?.bufferedReader().use { it?.readText() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "readFileContent failed for $name", e)
            null
        }
    }

    private fun readPdfContent(uri: Uri): String {
        // PDF text extraction using PDFBox Android port
        val sb = StringBuilder()
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val loader = com.tom_roush.pdfbox.android.PDFBoxResourceLoader()
                val document = com.tom_roush.pdfbox.pdmodel.PDDocument.load(stream)
                val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
                sb.append(stripper.getText(document))
                document.close()
            }
        } catch (e: Exception) {
            Log.w(TAG, "PDF extraction failed, trying raw text", e)
            // Fallback: try reading as raw bytes
            try {
                context.contentResolver.openInputStream(uri)?.bufferedReader().use {
                    val text = it?.readText() ?: ""
                    if (text.isNotBlank()) sb.append(text)
                }
            } catch (_: Exception) {}
        }
        return sb.toString()
    }

    private fun readPptxContent(uri: Uri): String {
        val sb = StringBuilder()
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val ppt = org.apache.poi.xslf.usermodel.XMLSlideShow(stream)
            ppt.use {
                for ((idx, slide) in it.slides.withIndex()) {
                    sb.appendLine("## 第${idx + 1}页")
                    val texts = slide.shapes.filterIsInstance<org.apache.poi.xslf.usermodel.XSLFTextShape>()
                    // First text shape with larger font is likely the title
                    val title = texts.firstOrNull { t ->
                        t.textParagraphs.any { p -> p.textRuns.any { r -> r.fontSize > 18.0 || r.isBold } }
                    } ?: texts.firstOrNull()
                    val body = texts.filter { it != title }
                    if (title != null) {
                        sb.appendLine("### ${title.text.trim()}")
                    }
                    for (shape in body) {
                        val text = shape.text.trim()
                        if (text.isNotBlank() && text.length > 2) {
                            // Bullet points
                            for (line in text.lines()) {
                                val trimmed = line.trim()
                                if (trimmed.isNotBlank()) sb.appendLine("- $trimmed")
                            }
                        }
                    }
                    sb.appendLine()
                }
            }
        }
        return sb.toString().ifBlank {
            // Fallback: simple text extraction
            val fallback = StringBuilder()
            context.contentResolver.openInputStream(uri)?.use { s ->
                val p = org.apache.poi.xslf.usermodel.XMLSlideShow(s)
                p.use { for (slide in it.slides) { for (shape in slide.shapes) { if (shape is org.apache.poi.xslf.usermodel.XSLFTextShape) fallback.appendLine(shape.text) } } }
            }
            fallback.toString()
        }
    }

    private fun readPptContent(uri: Uri): String {
        val sb = StringBuilder()
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val ppt = org.apache.poi.hslf.usermodel.HSLFSlideShow(stream)
            ppt.use {
                for (slide in it.slides) {
                    for (shape in slide.shapes) {
                        if (shape is org.apache.poi.hslf.usermodel.HSLFTextShape) {
                            sb.appendLine(shape.text)
                        }
                    }
                }
            }
        }
        return sb.toString()
    }

    private fun readExcelContent(uri: Uri): String {
        val sb = StringBuilder()
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val wb = org.apache.poi.ss.usermodel.WorkbookFactory.create(stream)
            wb.use {
                for (sheetIdx in 0 until it.numberOfSheets) {
                    val sheet = it.getSheetAt(sheetIdx)
                    sb.appendLine("--- ${it.getSheetName(sheetIdx)} ---")
                    for (row in sheet) {
                        val cols = (0 until row.lastCellNum.coerceAtMost(10))
                            .map { i -> row.getCell(i)?.toString()?.trim() ?: "" }
                            .filter { it.isNotBlank() }
                        if (cols.isNotEmpty()) sb.appendLine(cols.joinToString(" | "))
                    }
                }
            }
        }
        return sb.toString()
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
            name.endsWith(".pdf") -> "pdf"
            name.endsWith(".pptx") -> "pptx"
            name.endsWith(".ppt") -> "ppt"
            name.endsWith(".xlsx") || name.endsWith(".xls") || name.endsWith(".et") -> "excel"
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
你是知识库构建助手。请分析以下资料，提取核心知识点，为每个知识点生成 Wiki 页面。

资料可能是 PPT 幻灯片（每页以"## 第N页"标记，标题在"###"后，正文为"- "列表），也可能是纯文本或 Markdown。

每个页面用 --- 分隔，格式如下：

---
type: 规程
title: 知识点标题
tags: 标签1, 标签2
summary: 一句话概述
---

## 概述
简要说明该知识点是什么

## 详细内容
详细解释，可包含步骤、要点、注意事项等

## 相关概念
- [[相关概念]] — 关系说明

要求：
1. 每个独立知识点生成一个页面，type 选 concept/entity/procedure/definition
2. tags 用 3-5 个中文标签
3. [[ ]] 标注关联知识点
4. 中文输出
5. 内容少则生成 1-3 页，内容多则多页
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
