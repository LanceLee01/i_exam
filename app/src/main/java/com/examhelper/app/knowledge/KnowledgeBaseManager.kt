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

        /**
         * Hybrid text similarity: trigram(0.5) + bigram(0.3) + token(0.2).
         * Bigram gives better granularity for CJK; token captures whole-term overlap.
         */
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

        /**
         * Longest common subsequence ratio, normalized via Dice-like formula:
         *   2 * LCS / (len(a) + len(b))
         * Uses two-row DP: O(m*n) time, O(min(m,n)) space.
         * Suitable for borderline match rescue where n-gram methods fail.
         */
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
            buildFeatureCache()
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
        // For exact matching, use only the first 4000 chars — most question stems are within this range
        val shortQuery = if (normalizedQuery.length > 4000) normalizedQuery.take(4000) else normalizedQuery

        // Fast path: exact substring match (limited to short query for speed)
        val exactMatches = entries.filter {
            val normalizedQuestion = it.question.replace(Regex("（\\s*）"), "（）")
            shortQuery.contains(normalizedQuestion)
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

        // Build feature cache for all KBs
        for (kb in kbs) {
            if (kb.entries.isNotEmpty()) {
                kb.buildFeatureCache()
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
