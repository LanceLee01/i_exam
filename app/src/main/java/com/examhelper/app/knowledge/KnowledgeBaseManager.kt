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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID

data class KBEntry(
    val question: String,
    val answer: String,
    val source: String = "",
    val options: String = "",
    val questionType: String? = null  // "单选题"/"多选题"/"判断题"/null
) {
    companion object {
        // ── normalization ──────────────────────────────────────────────

        private fun normalizeText(text: String): String {
            return text
                .replace(Regex("\\s+"), "")
                .replace(Regex("[.,，。、；;：:！!？?（）()\\[\\]【】《》'\"“”]"), "")
        }

        // ── n-gram features ────────────────────────────────────────────

        fun computeTrigrams(text: String): Set<String> {
            val norm = normalizeText(text)
            if (norm.length < 3) return emptySet()
            val result = mutableSetOf<String>()
            for (i in 0..norm.length - 3) {
                result.add(norm.substring(i, i + 3))
            }
            return result
        }

        /** 2-char sliding window — better discrimination for CJK text. */
        fun computeBigrams(text: String): Set<String> {
            val norm = normalizeText(text)
            if (norm.length < 2) return emptySet()
            val result = mutableSetOf<String>()
            for (i in 0..norm.length - 2) {
                result.add(norm.substring(i, i + 2))
            }
            return result
        }

        /** Split on numbers/punctuation/whitespace, keep tokens ≥ 2 chars. */
        fun computeTokenSplits(text: String): Set<String> {
            return text.split(Regex("[0-9]+|[.,，。、；;：:！!？?（）()\\[\\]【】《》'\"“”\\s]+"))
                .map { it.trim() }
                .filter { it.length >= 2 }
                .toSet()
        }

        /** Set of unique characters after normalization — cheap pre-filter. */
        fun computeCharSet(text: String): Set<Char> {
            return normalizeText(text).toSet()
        }

        // ── similarity metrics ─────────────────────────────────────────

        fun jaccard(a: Set<String>, b: Set<String>): Float {
            if (a.isEmpty() || b.isEmpty()) return 0f
            val intersection = (a intersect b).size
            val union = (a union b).size
            return intersection.toFloat() / union.toFloat()
        }

        fun hybridTextScore(queryText: String, targetText: String): Float {
            val qTri = computeTrigrams(queryText)
            val tTri = computeTrigrams(targetText)
            val qBi = computeBigrams(queryText)
            val tBi = computeBigrams(targetText)
            val qTok = computeTokenSplits(queryText)
            val tTok = computeTokenSplits(targetText)

            val triScore = jaccard(qTri, tTri)
            val biScore = jaccard(qBi, tBi)
            val tokScore = if (qTok.isEmpty() || tTok.isEmpty()) 0f else {
                val intersection = (qTok intersect tTok).size
                val union = (qTok union tTok).size
                intersection.toFloat() / union.toFloat()
            }

            return triScore * 0.5f + biScore * 0.3f + tokScore * 0.2f
        }

        fun computeLCSRatio(a: String, b: String): Float {
            if (a.isEmpty() || b.isEmpty()) return 0f
            val shorter = if (a.length <= b.length) a else b
            val longer = if (a.length <= b.length) b else a
            var prev = IntArray(shorter.length + 1)
            var curr = IntArray(shorter.length + 1)
            for (i in 1..longer.length) {
                for (j in 1..shorter.length) {
                    curr[j] = if (longer[i - 1] == shorter[j - 1]) {
                        prev[j - 1] + 1
                    } else {
                        maxOf(prev[j], curr[j - 1])
                    }
                }
                prev = curr.also { curr = prev }
            }
            val lcsLen = prev[shorter.length]
            return (2.0f * lcsLen) / (longer.length + shorter.length).toFloat()
        }

        // ── hashing ────────────────────────────────────────────────────

        fun computeSHA256(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(bytes)
            return hash.joinToString("") { "%02x".format(it) }
        }
    }
}

/** 记录每次导入的文件信息 */
data class ImportRecord(
    val fileName: String,
    val hash: String,
    val importedAt: Long = System.currentTimeMillis(),
    val entryCount: Int = 0
)

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

// ── Feature data classes for search() ────────────────────────────────

private data class BlockFeatures(
    val trigrams: Set<String>,
    val bigrams: Set<String>,
    val tokens: Set<String>,
    val charSet: Set<Char>,
    val rawText: String
)

private data class OptionsFeatures(
    val trigrams: Set<String>,
    val bigrams: Set<String>,
    val tokens: Set<String>
)

private data class EntryFeatures(
    val entry: KBEntry,
    val questionTrigrams: Set<String>,
    val optionsTrigrams: Set<String>,
    val questionBigrams: Set<String>,
    val optionsBigrams: Set<String>,
    val questionTokens: Set<String>,
    val optionsTokens: Set<String>,
    val questionCharSet: Set<Char>
)

data class KnowledgeBase(
    val id: String = UUID.randomUUID().toString().take(8),
    val name: String = ""
) {
    val entries = mutableListOf<KBEntry>()
    val importedHashes = mutableSetOf<String>()
    val importRecords = mutableListOf<ImportRecord>()
    val count: Int get() = entries.size

    // Pre-computed feature cache for fast hybrid search
    private var featureCache: List<EntryFeatures>? = null

    fun buildFeatureCache() {
        featureCache = entries.map { entry ->
            EntryFeatures(
                entry = entry,
                questionTrigrams = KBEntry.computeTrigrams(entry.question),
                optionsTrigrams = KBEntry.computeTrigrams(entry.options),
                questionBigrams = KBEntry.computeBigrams(entry.question),
                optionsBigrams = KBEntry.computeBigrams(entry.options),
                questionTokens = KBEntry.computeTokenSplits(entry.question),
                optionsTokens = KBEntry.computeTokenSplits(entry.options),
                questionCharSet = KBEntry.computeCharSet(entry.question)
            )
        }
        Log.d("KnowledgeBase", "[$name] Built feature cache for ${entries.size} entries")
    }

    /** 更新指定索引的条目并重建缓存 */
    fun updateEntry(index: Int, entry: KBEntry) {
        if (index in entries.indices) {
            entries[index] = entry
            buildFeatureCache()
        }
    }

    /** 批量删除条目（传入索引集合，自动从高到低删除以避免索引偏移） */
    fun deleteEntries(indices: Set<Int>) {
        val sorted = indices.sortedDescending()
        for (idx in sorted) {
            if (idx in entries.indices) {
                entries.removeAt(idx)
            }
        }
        if (sorted.isNotEmpty()) {
            buildFeatureCache()
        }
    }

    /** 返回导入文件列表（按导入时间倒序） */
    fun getImportFiles(): List<ImportRecord> {
        return importRecords.sortedByDescending { it.importedAt }
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
                val qType = try { effectiveMapping.typeCol?.let { row.getCell(it)?.toString()?.trim() } } catch (_: Exception) { null } ?: ""
                entries.add(KBEntry(question, answer, source, options, qType))
                imported++
            }
            stream.close()
            Log.d("KnowledgeBase", "[$name] imported $imported entries, total=${entries.size}")
            buildFeatureCache()
            imported
        } catch (e: Exception) {
            Log.e("KnowledgeBase", "[$name] import failed", e)
            -1
        }
    }

    fun importExcelWithDedup(path: String, mapping: ColumnMapping? = null, displayFileName: String? = null): Int {
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
                val qType = try { effectiveMapping.typeCol?.let { row.getCell(it)?.toString()?.trim() } } catch (_: Exception) { null } ?: ""
                entries.add(KBEntry(question, answer, source, options, qType))
                imported++
            }
            stream.close()

            importedHashes.add(hash)
            // Record import file info
            val fileName = displayFileName ?: File(path).name
            importRecords.add(
                ImportRecord(
                    fileName = fileName,
                    hash = hash,
                    importedAt = System.currentTimeMillis(),
                    entryCount = imported  // imported is the count from this batch
                )
            )
            Log.d("KnowledgeBase", "[$name] Recorded import: $fileName ($imported entries)")
            Log.d("KnowledgeBase", "[$name] imported $imported entries, total=${entries.size}")
            buildFeatureCache()
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

    fun search(query: String, options: String = "", topN: Int = 300): List<Pair<KBEntry, Float>> {
        if (entries.isEmpty()) return emptyList()

        // 规范化括号内空格，兼容不同数量的空格
        val normalizedQuery = query.replace(Regex("（\\s*）"), "（）")

        // Fast path: exact substring match (search entire query — don't truncate)
        val exactMatches = entries.filter {
            val normalizedQuestion = it.question.replace(Regex("（\\s*）"), "（）")
            normalizedQuery.contains(normalizedQuestion)
        }
        val exactSet = exactMatches.toSet()

        // ── Pre-compute query block features ──
        val blocks = extractQuestionBlocks(query)
        // Only pre-compute trigrams (fast); hybrid features computed lazily for promising entries
        val blockTrigrams = blocks.map { KBEntry.computeTrigrams(it) }
        val blockChars = blocks.map { KBEntry.computeCharSet(it) }

        val queryOptionsTrigrams = if (options.isNotBlank()) KBEntry.computeTrigrams(options) else null

        val allScored = exactMatches.map { it to 1.0f }.toMutableList()

        // ── Two-stage scoring: fast trigram filter → hybrid for promising entries ──
        val remaining = if (featureCache != null) {
            featureCache!!.filter { it.entry !in exactSet }
        } else {
            // Fast path: only precompute what's needed for trigram filter
            Log.d("KnowledgeBase", "[$name] featureCache is NULL, building on-the-fly trigram entries (limited to 3000)")
            entries
                .filter { it !in exactSet }
                .take(3000)
                .map { entry ->
                    EntryFeatures(
                        entry = entry,
                        questionTrigrams = KBEntry.computeTrigrams(entry.question),
                        optionsTrigrams = emptySet(), // lazy
                        questionBigrams = emptySet(),  // lazy
                        optionsBigrams = emptySet(),   // lazy
                        questionTokens = emptySet(),   // lazy
                        optionsTokens = emptySet(),    // lazy
                        questionCharSet = KBEntry.computeCharSet(entry.question)
                    )
                }
        }

        val EARLY_TERM_FACTOR = 0.05f

        for ((index, feat) in remaining.withIndex()) {
            // ── Stage 1: Fast charSet + trigram pre-filter ──
            // Find the best-matching block via character set overlap (cheap)
            val bestBlockIdx = blockChars.indices.maxByOrNull { i ->
                if (blockChars[i].isEmpty() || feat.questionCharSet.isEmpty()) 0
                else (blockChars[i] intersect feat.questionCharSet).size
            } ?: -1
            if (bestBlockIdx < 0) continue
            val bestCharOverlap = if (blockChars[bestBlockIdx].isNotEmpty())
                (blockChars[bestBlockIdx] intersect feat.questionCharSet).size.toFloat() /
                blockChars[bestBlockIdx].size.toFloat().coerceAtLeast(1f) else 0f
            if (bestCharOverlap < 0.10f) continue

            // Fast trigram score against best block only
            var score = KBEntry.jaccard(blockTrigrams[bestBlockIdx], feat.questionTrigrams)

            if (score < 0.12f) continue

            // ── Stage 2: Hybrid scoring for promising entries (≥ 0.12 trigram) ──
            // Compute lazy features if not cached
            val qBigrams = if (feat.questionBigrams.isNotEmpty()) feat.questionBigrams
                else KBEntry.computeBigrams(feat.entry.question)
            val qTokens = if (feat.questionTokens.isNotEmpty()) feat.questionTokens
                else KBEntry.computeTokenSplits(feat.entry.question)

            // Hybrid score against the best block
            val bestBlockText = blocks[bestBlockIdx]
            val bBigrams = KBEntry.computeBigrams(bestBlockText)
            val bTokens = KBEntry.computeTokenSplits(bestBlockText)
            val biScore = KBEntry.jaccard(bBigrams, qBigrams)
            val tokScore = if (bTokens.isEmpty() || qTokens.isEmpty()) 0f else {
                val inter = (bTokens intersect qTokens).size
                val union = (bTokens union qTokens).size
                inter.toFloat() / union.toFloat()
            }
            score = score * 0.5f + biScore * 0.3f + tokScore * 0.2f

            // ── Options similarity (optimization 5) ──
            if (queryOptionsTrigrams != null && feat.entry.options.isNotBlank()) {
                val entryOptTrigrams = if (feat.optionsTrigrams.isNotEmpty()) feat.optionsTrigrams
                    else KBEntry.computeTrigrams(feat.entry.options)
                val optScore = KBEntry.jaccard(queryOptionsTrigrams, entryOptTrigrams)
                score = score * 0.6f + optScore * 0.4f
            }

            // ── LCS rescue for borderline matches (optimization 2) ──
            if (score in 0.15f..0.50f) {
                val lcsRatio = KBEntry.computeLCSRatio(
                    bestBlockText.replace(Regex("\\s+"), ""),
                    feat.entry.question.replace(Regex("\\s+"), "")
                )
                score = when {
                    lcsRatio > 0.70f -> maxOf(score, lcsRatio * 0.85f)
                    lcsRatio > 0.50f -> (score + lcsRatio) / 2.0f
                    else -> score
                }
            }

            if (score < 0.15f) continue
            allScored.add(feat.entry to score)

            // ── Early termination (optimization 4) ──
            if (allScored.size % 500 == 0 && allScored.size >= topN) {
                val recentAvg = allScored.takeLast(100).map { it.second }.average().toFloat()
                if (recentAvg < 0.05f) {
                    Log.d("KnowledgeBase", "[$name] Early termination at entry $index, recentAvg=$recentAvg")
                    break
                }
            }
        }

        return allScored
            // Sort by score, but boost longer questions to prevent short generic
            // questions (e.g. "组织措施有（ ）") from stealing matches from longer
            // specific questions (e.g. "组织措施有工作票制度...（ ）")
            .sortedByDescending { (entry, score) ->
                score + (entry.question.length / 500f).coerceAtMost(0.05f)
            }
            .take(topN)
    }

}

private fun KnowledgeBase.isGarbled(text: String): Boolean {
    if (text.contains('\uFFFD')) return true
    for (c in text) {
        val code = c.code
        if (code in 0x0000..0x0008 || code == 0x000B || code == 0x000C || code in 0x000E..0x001F) return true
    }
    if (Regex("[?？]{3,}").containsMatchIn(text)) return true
    return false
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

        // Auto-import asset files on first launch (KB empty)
        val kbName = "安规2026_信息通信"
        var kb = kbs.firstOrNull { it.name == kbName }
        val needsImport = kb == null || kb.entries.isEmpty()

        if (needsImport) {
            // Remove old KBs
            kbs.removeAll { it.name == "安规2026" || it.name == "安规" || it.name == kbName }
            kb = KnowledgeBase(name = kbName)
            kbs.add(kb)
            activeIndex = kbs.size - 1

            // Import asset Excel files
            val assetFiles = listOf(
                "D类-一线人员.et",
                "34-通信安规.xls",
                "35-信息安规.xls"
            )
            for (assetName in assetFiles) {
                try {
                    val tmpFile = File(context.filesDir, assetName)
                    context.assets.open(assetName).use { input ->
                        tmpFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    val count = kb.importExcelWithDedup(tmpFile.absolutePath, displayFileName = assetName)
                    Log.d("KBManager", "Imported $assetName: $count entries")
                } catch (e: Exception) {
                    Log.e("KBManager", "Failed to import $assetName", e)
                }
            }
            kb.buildFeatureCache()
            save()
            Log.d("KBManager", "Initialized KB: ${kb.name} with ${kb.entries.size} entries")
        } else {
            // Remove old KBs with old names
            val oldKB = kbs.firstOrNull { it.name == "安规2026" }
            if (oldKB != null) kbs.remove(oldKB)
        }

        // Set active KB
        if (activeIndex < 0 && kbs.isNotEmpty()) {
            activeIndex = 0
            save()
        }

        // Build feature cache for all KBs
        for (kb in kbs) {
            if (kb.entries.isNotEmpty()) {
                kb.buildFeatureCache()
            }
        }

        // 迁移：为旧数据补充导入文件记录（反向匹配 asset 文件哈希）
        for (kb in kbs) {
            val allPlaceholders = kb.importRecords.all { it.importedAt == 0L }
            if ((kb.importRecords.isEmpty() || allPlaceholders) && kb.importedHashes.isNotEmpty()) {
                // 清除旧的占位记录
                kb.importRecords.clear()
                val assetFiles = listOf(
                    "D类-一线人员.et",
                    "34-通信安规.xls",
                    "35-信息安规.xls",
                    "1-习总书记安全生产重要论述.xls",
                    "33-通信安规.xls"
                )
                for (assetName in assetFiles) {
                    try {
                        val tmpFile = File(context.filesDir, "migrate_$assetName")
                        context.assets.open(assetName).use { input ->
                            tmpFile.outputStream().use { output -> input.copyTo(output) }
                        }
                        val hash = KBEntry.computeSHA256(tmpFile.readBytes())
                        if (hash in kb.importedHashes) {
                            kb.importRecords.add(
                                ImportRecord(
                                    fileName = assetName,
                                    hash = hash,
                                    importedAt = System.currentTimeMillis(),
                                    entryCount = 0
                                )
                            )
                            Log.d("KBManager", "Migrated import record: $assetName")
                        }
                    } catch (_: Exception) { }
                }
                // 未能匹配的哈希用占位记录
                val matched = kb.importRecords.map { it.hash }.toSet()
                for (hash in kb.importedHashes) {
                    if (hash !in matched) {
                        kb.importRecords.add(
                            ImportRecord(
                                fileName = "历史导入记录",
                                hash = hash,
                                importedAt = System.currentTimeMillis(),
                                entryCount = 0
                            )
                        )
                    }
                }
                if (kb.importRecords.isNotEmpty()) {
                    save()
                    Log.d("KBManager", "Populated ${kb.importRecords.size} import records for ${kb.name}")
                }
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
                        importedHashes = kb.importedHashes.toList(),
                        importRecords = kb.importRecords.toList()
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
                // Fix null questionType from old JSON data (Gson doesn't use Kotlin defaults)
                kb.entries.addAll(kd.entries)
                kb.importedHashes.addAll(kd.importedHashes)
                // 兼容旧数据：如果 importRecords 为空但 importedHashes 有值，生成占位记录
                if (kd.importRecords.isNullOrEmpty() && kd.importedHashes.isNotEmpty()) {
                    kb.importRecords.addAll(kd.importedHashes.map { hash ->
                        ImportRecord(
                            fileName = "历史导入记录",
                            hash = hash,
                            importedAt = 0L,
                            entryCount = 0
                        )
                    })
                } else if (!kd.importRecords.isNullOrEmpty()) {
                    kb.importRecords.addAll(kd.importRecords)
                }
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

                val count = kb.importExcelWithDedup(file.absolutePath, displayFileName = file.name)
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
    val importedHashes: List<String>,
    val importRecords: List<ImportRecord> = emptyList()
)

private data class KBStorageData(
    val kbs: List<KBData>,
    val activeKbIndex: Int
)
