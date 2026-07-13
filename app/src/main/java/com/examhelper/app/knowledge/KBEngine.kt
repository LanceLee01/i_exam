package com.examhelper.app.knowledge

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.examhelper.app.knowledge.db.AppDatabase
import com.examhelper.app.knowledge.db.SourceFile
import com.examhelper.app.knowledge.db.WikiPage
import com.examhelper.app.knowledge.db.Wikilink
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

            // 根据内容哈希去重（相同内容不重复导入）
            val existingByHash = db.sourceFileDao().getByHash(hash)
            if (existingByHash != null) {
                Log.d(TAG, "SHA256匹配，跳过重复导入: $path (与 ${existingByHash.filePath} 相同)")
                return@withContext ImportResult(true, skipped = true)
            }

            val fileName = getFileName(uri)
            // 如果路径已导入过（文件更新），复用已有来源ID
            val existingByPath = db.sourceFileDao().getByPath(path)
            val sourceId = existingByPath?.id ?: UUID.randomUUID().toString()

            // 直接使用原始文本，无需LLM处理
            val truncated = if (content.length > MAX_DOC_CHARS) {
                content.take(MAX_DOC_CHARS) + "\n\n[文档过长，已截断]"
            } else content

            // 将原始文本按段落分割为知识页面
            val rawTitle = fileName.removeSuffix(".pptx").removeSuffix(".ppt").removeSuffix(".pdf")
                .removeSuffix(".xlsx").removeSuffix(".xls").removeSuffix(".txt").removeSuffix(".md")

            // 尝试按常见分隔符拆分内容为多个段落
            val sections = splitIntoSections(truncated)

            val pages = if (sections.size > 1) {
                sections.mapIndexed { idx, section ->
                    val sectionTitle = extractSectionTitle(section) ?: "${rawTitle} - 第${idx + 1}部分"
                    val sectionSummary = section.lines().firstOrNull()?.take(200)?.trim() ?: "知识段落"
                    WikiPage(
                        title = sectionTitle,
                        content = section.take(MAX_CONTENT_LENGTH),
                        summary = sectionSummary,
                        pageType = "concept",
                        tags = "知识, 文档",
                        sources = sourceId
                    )
                }
            } else {
                // 单段落作为整体页面
                listOf(WikiPage(
                    title = rawTitle.ifBlank { "未命名文档" },
                    content = truncated.take(MAX_CONTENT_LENGTH),
                    summary = truncated.lines().firstOrNull()?.take(200)?.trim() ?: "知识页面",
                    pageType = "concept",
                    tags = "知识, 文档",
                    sources = sourceId
                ))
            }

            // 如果解析结果为空，创建兜底页面
            val finalPages = if (pages.isEmpty()) {
                Log.w(TAG, "解析结果为空，创建兜底页面")
                listOf(WikiPage(
                    title = rawTitle.ifBlank { "未命名文档" },
                    content = truncated.take(MAX_CONTENT_LENGTH),
                    summary = truncated.lines().firstOrNull()?.take(200)?.trim() ?: "知识页面",
                    pageType = "concept",
                    tags = "知识, 文档",
                    sources = sourceId
                ))
            } else pages

            // 截断过长的页面内容
            val truncatedPages = finalPages.map { page ->
                if (page.content.length > MAX_CONTENT_LENGTH) {
                    Log.w(TAG, "截断页面 '${page.title}' 内容：${page.content.length} -> $MAX_CONTENT_LENGTH 字符")
                    page.copy(content = page.content.take(MAX_CONTENT_LENGTH))
                } else page
            }

            db.wikiPageDao().insertAll(truncatedPages)
            Log.d(TAG, "已插入 ${truncatedPages.size} 个知识页面")

            val sourceFile = SourceFile(
                id = sourceId,
                filePath = path,
                fileName = fileName,
                fileType = getFileType(uri),
                contentHash = hash,
                pageCount = truncatedPages.size
            )
            db.sourceFileDao().insert(sourceFile)

            // 提取并插入页面间的Wiki链接
            val allTitles = truncatedPages.map { it.uid to it.title }.toMap()
            val links = mutableListOf<Wikilink>()
            for (page in truncatedPages) {
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
                Log.d(TAG, "已插入 ${links.size} 个Wiki链接")
            }

            ImportResult(success = true, pagesGenerated = truncatedPages.size)
        } catch (e: Exception) {
            Log.e(TAG, "导入文件失败", e)
            ImportResult(false, error = e.message ?: "未知错误")
        }
    }

    suspend fun searchByQuestion(questionText: String): SearchResult {
        // 使用纯文本搜索（FTS + SQL LIKE），无embedding
        val ftsPages = try {
            db.wikiPageDao().searchByTitleLike(questionText)
        } catch (e: Exception) {
            Log.w(TAG, "标题搜索失败: ${e.message}")
            emptyList()
        }

        // 使用FTS全文搜索
        val fullTextPages = try {
            val query = buildFtsQuery(questionText)
            db.wikiPageDao().searchFts(query)
        } catch (e: Exception) {
            Log.w(TAG, "全文搜索失败: ${e.message}")
            emptyList<WikiPage>()
        }

        // 合并去重
        val seen = mutableSetOf<Long>()
        val allPages = mutableListOf<WikiPage>()
        val mergedPages = ftsPages + fullTextPages
        for (page in mergedPages) {
            if (page.id !in seen) {
                seen.add(page.id)
                allPages.add(page)
            }
        }

        return SearchResult(
            pages = allPages.take(10),
            ftsPages = ftsPages,
            trigramPages = fullTextPages
        )
    }

    suspend fun canAnswerFromKB(questionText: String, pages: List<WikiPage>): Boolean {
        return pages.isNotEmpty()
    }

    suspend fun getAnswerFromKB(questionText: String, pages: List<WikiPage>): String? {
        if (pages.isEmpty()) return null
        // 直接拼接匹配页面的内容返回，无需LLM
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
            Log.e(TAG, "读取文件内容失败: $name", e)
            null
        }
    }

    private fun readPdfContent(uri: Uri): String {
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
            Log.w(TAG, "PDF提取失败，尝试原始文本读取", e)
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

    /**
     * 将原始文本按常见分隔符拆分为多个段落
     * 支持PPT格式（## 第N页）、Markdown标题（#）、双换行分段
     */
    private fun splitIntoSections(text: String): List<String> {
        // 先尝试按PPT幻灯片格式拆分
        val slidePattern = Regex("""(?=## 第\d+页)""")
        val slideSections = text.split(slidePattern).filter { it.trim().isNotEmpty() }
        if (slideSections.size > 1) {
            return slideSections.map { it.trim() }
        }

        // 再尝试按Markdown标题拆分（二级标题及以上）
        val headingPattern = Regex("""(?=^#{1,3}\s)""", RegexOption.MULTILINE)
        val headingSections = text.split(headingPattern).filter { it.trim().isNotEmpty() }
        if (headingSections.size > 1) {
            return headingSections.map { it.trim() }
        }

        // 最后按双换行分段
        val paraSections = text.split(Regex("""\n{2,}""")).filter { it.trim().isNotEmpty() }
        if (paraSections.size > 1) {
            return paraSections.map { it.trim() }
        }

        return listOf(text.trim())
    }

    /**
     * 从段落中提取标题（优先取第一行 ## 或 ### 后的文本）
     */
    private fun extractSectionTitle(section: String): String? {
        val lines = section.lines()
        for (line in lines) {
            val trimmed = line.trim()
            val match = Regex("""^#{1,3}\s+(.+)""").find(trimmed)
            if (match != null) {
                return match.groupValues[1].trim()
            }
        }
        return null
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
            .replace(Regex("""[.,，。、；;：:！!？?（）()\[\]【】《》'"'"'"\s]+"""), " ")
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
        const val MAX_CONTENT_LENGTH = 10000

        @Volatile
        private var instance: KBEngine? = null

        fun getInstance(context: Context): KBEngine {
            return instance ?: synchronized(this) {
                instance ?: KBEngine(context.applicationContext).also { instance = it }
            }
        }
    }
}
