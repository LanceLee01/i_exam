package com.examhelper.app.knowledge

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import org.apache.poi.ss.usermodel.WorkbookFactory
import com.examhelper.app.ExamApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.UUID

data class KBEntry(
    val question: String,
    val answer: String,
    val source: String = "",
    val options: String = ""
) {
    companion object {
        fun computeTrigrams(text: String): Set<String> {
            if (text.length < 3) return emptySet()
            val norm = text
                .replace(Regex("\\s+"), "")
                .replace(Regex("[.,，。、；;：:！!？?（）()\\[\\]【】《》'\"“”]"), "")
            if (norm.length < 3) return emptySet()
            val result = mutableSetOf<String>()
            for (i in 0..norm.length - 3) {
                result.add(norm.substring(i, i + 3))
            }
            return result
        }

        fun jaccard(a: Set<String>, b: Set<String>): Float {
            if (a.isEmpty() || b.isEmpty()) return 0f
            val intersection = (a intersect b).size
            val union = (a union b).size
            return intersection.toFloat() / union.toFloat()
        }

        fun computeSHA256(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(bytes)
            return hash.joinToString("") { "%02x".format(it) }
        }
    }
}

/** Split exam text into individual question blocks by "N、" pattern */
private fun extractQuestionBlocks(text: String): List<String> {
    val pattern = Regex("""(\d+)、""")
    val matches = pattern.findAll(text).toList()
    if (matches.size <= 1) return listOf(text)  // Single question or no clear split
    val blocks = mutableListOf<String>()
    for (i in matches.indices) {
        val start = matches[i].range.first
        val end = if (i + 1 < matches.size) matches[i + 1].range.first else text.length
        blocks.add(text.substring(start, end).trim())
    }
    return blocks
}

data class KnowledgeBase(
    val id: String = UUID.randomUUID().toString().take(8),
    val name: String = ""
) {
    val entries = mutableListOf<KBEntry>()
    val importedHashes = mutableSetOf<String>()
    val count: Int get() = entries.size

    // Pre-computed trigram cache for fast search
    private var trigramCache: List<Triple<KBEntry, Set<String>, Set<String>>>? = null

    fun buildTrigramCache() {
        trigramCache = entries.map { entry ->
            Triple(entry, KBEntry.computeTrigrams(entry.question), KBEntry.computeTrigrams(entry.options))
        }
        Log.d("KnowledgeBase", "[$name] Built trigram cache for ${entries.size} entries")
    }

    fun importExcel(path: String, mapping: ColumnMapping? = null): Int {
        return try {
            val effectiveMapping: ColumnMapping
            if (mapping != null) {
                effectiveMapping = mapping
            } else {
                val detected = autoDetectColumns(path)
                if (detected == null) {
                    val config = runBlocking { ExamApplication.instance.appConfig.getSnapshot() }
                    return if (config.apiKey.isBlank()) -3 else -4
                }
                effectiveMapping = detected
            }

            val stream = FileInputStream(path)
            val workbook = WorkbookFactory.create(stream)
            val sheet = workbook.getSheetAt(0)
            var imported = 0
            for (row in sheet) {
                if (row.rowNum == 0) continue
                val question = row.getCell(effectiveMapping.questionCol)?.toString()?.trim() ?: continue
                val answer = row.getCell(effectiveMapping.answerCol)?.toString()?.trim()
                if (question.isBlank() || answer.isNullOrBlank()) continue
                val source = try { effectiveMapping.sourceCol?.let { row.getCell(it)?.toString()?.trim() } } catch (_: Exception) { null } ?: ""
                val options = try { effectiveMapping.optionsCol?.let { row.getCell(it)?.toString()?.trim() } } catch (_: Exception) { null } ?: ""
                entries.add(KBEntry(question, answer, source, options))
                imported++
            }
            stream.close()
            Log.d("KnowledgeBase", "[$name] imported $imported entries, total=${entries.size}")
            buildTrigramCache()
            imported
        } catch (e: Exception) {
            Log.e("KnowledgeBase", "[$name] import failed", e)
            -1
        }
    }

    fun importExcelWithDedup(path: String, mapping: ColumnMapping? = null): Int {
        return try {
            val file = File(path)
            val bytes = file.readBytes()
            val hash = KBEntry.computeSHA256(bytes)

            if (hash in importedHashes) {
                Log.d("KnowledgeBase", "[$name] SHA256 match, skipping duplicate import")
                return -2
            }

            val effectiveMapping: ColumnMapping
            if (mapping != null) {
                effectiveMapping = mapping
            } else {
                val detected = autoDetectColumns(path)
                if (detected == null) {
                    val config = runBlocking { ExamApplication.instance.appConfig.getSnapshot() }
                    return if (config.apiKey.isBlank()) -3 else -4
                }
                effectiveMapping = detected
            }

            val stream = FileInputStream(file)
            val workbook = WorkbookFactory.create(stream)
            val sheet = workbook.getSheetAt(0)
            var imported = 0
            for (row in sheet) {
                if (row.rowNum == 0) continue
                val question = row.getCell(effectiveMapping.questionCol)?.toString()?.trim() ?: continue
                val answer = row.getCell(effectiveMapping.answerCol)?.toString()?.trim()
                if (question.isBlank() || answer.isNullOrBlank()) continue
                val source = try { effectiveMapping.sourceCol?.let { row.getCell(it)?.toString()?.trim() } } catch (_: Exception) { null } ?: ""
                val options = try { effectiveMapping.optionsCol?.let { row.getCell(it)?.toString()?.trim() } } catch (_: Exception) { null } ?: ""
                entries.add(KBEntry(question, answer, source, options))
                imported++
            }
            stream.close()

            importedHashes.add(hash)
            Log.d("KnowledgeBase", "[$name] imported $imported entries, total=${entries.size}")
            buildTrigramCache()
            imported
        } catch (e: Exception) {
            Log.e("KnowledgeBase", "[$name] import failed", e)
            -1
        }
    }

    private fun autoDetectColumns(path: String): ColumnMapping? {
        return try {
            runBlocking(Dispatchers.IO) {
                ColumnDetector().detectColumns(path)
            }
        } catch (e: ColumnDetectionException) {
            Log.w("KnowledgeBase", "autoDetectColumns failed: ${e.reason}")
            null
        }
    }

    fun search(query: String, options: String = "", topN: Int = 50): List<Pair<KBEntry, Float>> {
        if (entries.isEmpty()) return emptyList()

        // 规范化括号内空格，兼容不同数量的空格
        val normalizedQuery = query.replace(Regex("（\\s*）"), "（）")

        // Fast path: exact substring match
        val exactMatches = entries.filter {
            val normalizedQuestion = it.question.replace(Regex("（\\s*）"), "（）")
            normalizedQuery.contains(normalizedQuestion)
        }
        val exactSet = exactMatches.toSet()

        if (exactMatches.size >= topN) {
            return exactMatches.map { it to 1.0f }.take(topN)
        }

        // Use trigram cache if available, otherwise compute on-the-fly
        val blocks = extractQuestionBlocks(query)
        val blockTrigrams = blocks.map { KBEntry.computeTrigrams(it) }
        val queryOptionsTrigrams = if (options.isNotBlank()) KBEntry.computeTrigrams(options) else null

        val trigramResults = if (trigramCache != null) {
            // Use cached trigrams for fast search
            trigramCache!!
                .filter { (entry, _, _) -> entry !in exactSet }
                .map { (entry, entryTrigrams, entryOptionsTrigrams) ->
                    var score = blockTrigrams.maxOfOrNull { blockTri ->
                        KBEntry.jaccard(blockTri, entryTrigrams)
                    } ?: 0f

                    // Boost score if options are similar
                    if (queryOptionsTrigrams != null && entry.options.isNotBlank() && score > 0.3f) {
                        val optionsSimilarity = KBEntry.jaccard(queryOptionsTrigrams, entryOptionsTrigrams)
                        if (optionsSimilarity > 0.7f) {
                            score = (score * 0.7f + optionsSimilarity * 0.3f).coerceAtMost(1.0f)
                        }
                    }

                    entry to score
                }
                .filter { it.second > 0.15f }
        } else {
            // Fallback: compute trigrams on-the-fly (limited to 5000)
            entries
                .filter { it !in exactSet }
                .take(5000)
                .map { entry ->
                    var score = blockTrigrams.maxOfOrNull { blockTri ->
                        KBEntry.jaccard(blockTri, KBEntry.computeTrigrams(entry.question))
                    } ?: 0f

                    // Boost score if options are similar
                    if (queryOptionsTrigrams != null && entry.options.isNotBlank() && score > 0.3f) {
                        val entryOptionsTrigrams = KBEntry.computeTrigrams(entry.options)
                        val optionsSimilarity = KBEntry.jaccard(queryOptionsTrigrams, entryOptionsTrigrams)
                        if (optionsSimilarity > 0.7f) {
                            score = (score * 0.7f + optionsSimilarity * 0.3f).coerceAtMost(1.0f)
                        }
                    }

                    entry to score
                }
                .filter { it.second > 0.15f }
        }

        return (exactMatches.map { it to 1.0f } + trigramResults)
            .sortedByDescending { it.second }
            .take(topN)
    }
}

object KnowledgeBaseManager {

    private val gson = GsonBuilder().create()
    private val kbs = mutableListOf<KnowledgeBase>()
    private var activeIndex = -1

    private lateinit var storageFile: File

    val activeKB: KnowledgeBase? get() = kbs.getOrNull(activeIndex)
    val allKBs: List<KnowledgeBase> get() = kbs
    val activeKBName: String get() = activeKB?.name ?: "无"

    fun init(context: Context) {
        storageFile = File(context.filesDir, "kb_data.json")
        load()

        // Load or reload default knowledge base from assets
        val existingKB = kbs.firstOrNull { it.name == "安规2026" }
        val oldKB = kbs.firstOrNull { it.name == "安规" }
        val needsReload = existingKB == null || existingKB.entries.size < 10000

        if (needsReload) {
            try {
                // Always re-copy from assets to ensure latest version
                val defaultKBFile = File(context.filesDir, "default_kb.json")
                defaultKBFile.delete()
                context.assets.open("default_kb.json").use { input ->
                    defaultKBFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                val json = defaultKBFile.readText()
                val defaultData = gson.fromJson(json, KBStorageData::class.java)
                if (defaultData.kbs.isNotEmpty()) {
                    val defaultKBData = defaultData.kbs[0]
                    val kb = KnowledgeBase(id = defaultKBData.id, name = defaultKBData.name)
                    kb.entries.addAll(defaultKBData.entries)
                    kb.importedHashes.addAll(defaultKBData.importedHashes)

                    // Remove old KBs with old names
                    if (existingKB != null) kbs.remove(existingKB)
                    if (oldKB != null) kbs.remove(oldKB)

                    kbs.add(kb)
                    activeIndex = kbs.size - 1
                    save()
                    Log.d("KBManager", "Loaded default KB: ${kb.name} with ${kb.entries.size} entries")
                }
            } catch (e: Exception) {
                Log.e("KBManager", "Failed to load default KB", e)
            }
        }

        // Set active KB
        if (activeIndex < 0 && kbs.isNotEmpty()) {
            activeIndex = 0
            save()
        }

        // Build trigram cache for all KBs
        for (kb in kbs) {
            if (kb.entries.isNotEmpty()) {
                kb.buildTrigramCache()
            }
        }
    }

    fun addKB(name: String): KnowledgeBase {
        val kb = KnowledgeBase(name = name)
        kbs.add(kb)
        activeIndex = kbs.size - 1
        save()
        return kb
    }

    fun deleteKB(index: Int) {
        if (index !in kbs.indices) return
        kbs.removeAt(index)
        if (activeIndex > index) activeIndex--
        else if (activeIndex == index) activeIndex = (index - 1).coerceAtLeast(0)
        if (kbs.isEmpty()) activeIndex = -1
        save()
    }

    fun selectKB(index: Int) {
        if (index in kbs.indices) {
            activeIndex = index
            save()
        }
    }

    fun save() {
        try {
            val data = KBStorageData(
                kbs = kbs.map { kb ->
                    KBData(
                        id = kb.id,
                        name = kb.name,
                        entries = kb.entries,
                        importedHashes = kb.importedHashes.toList()
                    )
                },
                activeKbIndex = activeIndex
            )
            storageFile.writeText(gson.toJson(data))
        } catch (e: Exception) {
            Log.e("KBManager", "save failed", e)
        }
    }

    private fun load() {
        try {
            if (!storageFile.exists()) return
            val json = storageFile.readText()
            val data = gson.fromJson(json, KBStorageData::class.java)
            kbs.clear()
            for (kd in data.kbs) {
                val kb = KnowledgeBase(id = kd.id, name = kd.name)
                kb.entries.addAll(kd.entries)
                kb.importedHashes.addAll(kd.importedHashes)
                kbs.add(kb)
            }
            activeIndex = data.activeKbIndex.coerceIn(-1, kbs.size - 1)
            Log.d("KBManager", "Loaded ${kbs.size} KBs, active=$activeIndex")
        } catch (e: Exception) {
            Log.e("KBManager", "load failed", e)
        }
    }

    suspend fun importZipFromUri(
        context: Context,
        zipUri: Uri,
        onProgress: (current: Int, total: Int, fileName: String) -> Unit
    ): ZipImportHelper.ZipImportResult = withContext(Dispatchers.IO) {
        val excelFiles = ZipImportHelper.extractExcelFiles(context, zipUri)
            ?: return@withContext ZipImportHelper.ZipImportResult(
                success = false,
                error = "ZIP 文件格式错误或无 Excel 文件"
            )

        var importedEntries = 0
        var skippedFiles = 0
        var failedFiles = 0

        excelFiles.forEachIndexed { index, file ->
            onProgress(index + 1, excelFiles.size, file.name)

            try {
                val kb = activeKB ?: return@withContext ZipImportHelper.ZipImportResult(
                    success = false,
                    error = "请先激活知识库"
                )

                val count = kb.importExcelWithDedup(file.absolutePath)
                when {
                    count == -2 -> skippedFiles++
                    count == -3 || count == -4 -> failedFiles++
                    count >= 0 -> {
                        importedEntries += count
                        save()
                    }
                    else -> failedFiles++
                }
            } catch (e: Exception) {
                failedFiles++
            }
        }

        ZipImportHelper.cleanupTempFiles(context)

        ZipImportHelper.ZipImportResult(
            success = importedEntries > 0 || skippedFiles > 0,
            totalFiles = excelFiles.size,
            excelFiles = excelFiles.size,
            importedEntries = importedEntries,
            skippedFiles = skippedFiles,
            failedFiles = failedFiles
        )
    }
}

private data class KBData(
    val id: String,
    val name: String,
    val entries: List<KBEntry>,
    val importedHashes: List<String>
)

private data class KBStorageData(
    val kbs: List<KBData>,
    val activeKbIndex: Int
)
