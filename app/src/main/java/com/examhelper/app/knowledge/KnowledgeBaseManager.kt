package com.examhelper.app.knowledge

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.UUID

data class KBEntry(
    val question: String,
    val answer: String,
    val source: String = ""
) {
    val trigrams: Set<String> by lazy { computeTrigrams(question) }

    companion object {
        fun computeTrigrams(text: String): Set<String> {
            val norm = text
                .replace(Regex("\\s+"), "")
                .replace(Regex("[.,，。、；;：:！!？?（）()\\[\\]【】《》'\"“”]"), "")
            val result = mutableSetOf<String>()
            if (norm.length >= 3) {
                for (i in 0..norm.length - 3) {
                    result.add(norm.substring(i, i + 3))
                }
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

data class KnowledgeBase(
    val id: String = UUID.randomUUID().toString().take(8),
    val name: String = ""
) {
    val entries = mutableListOf<KBEntry>()
    val importedHashes = mutableSetOf<String>()
    val count: Int get() = entries.size

    fun importExcel(path: String): Int {
        return try {
            val stream = FileInputStream(path)
            val workbook = WorkbookFactory.create(stream)
            val sheet = workbook.getSheetAt(0)
            var imported = 0
            for (row in sheet) {
                if (row.rowNum == 0) continue
                val question = row.getCell(5)?.toString()?.trim() ?: continue  // F列
                val answer = row.getCell(7)?.toString()?.trim()                // H列
                if (question.isBlank() || answer.isNullOrBlank()) continue
                val source = try { row.getCell(6)?.toString()?.trim() } catch (_: Exception) { null }  // G列（选项，作来源）
                entries.add(KBEntry(question, answer, source ?: ""))
                imported++
            }
            stream.close()
            Log.d("KnowledgeBase", "[$name] imported $imported entries, total=${entries.size}")
            imported
        } catch (e: Exception) {
            Log.e("KnowledgeBase", "[$name] import failed", e)
            -1
        }
    }

    fun importExcelWithDedup(path: String): Int {
        return try {
            val file = File(path)
            val bytes = file.readBytes()
            val hash = KBEntry.computeSHA256(bytes)

            if (hash in importedHashes) {
                Log.d("KnowledgeBase", "[$name] SHA256 match, skipping duplicate import")
                return -2
            }

            val stream = FileInputStream(file)
            val workbook = WorkbookFactory.create(stream)
            val sheet = workbook.getSheetAt(0)
            var imported = 0
            for (row in sheet) {
                if (row.rowNum == 0) continue
                val question = row.getCell(5)?.toString()?.trim() ?: continue  // F列
                val answer = row.getCell(7)?.toString()?.trim()                // H列
                if (question.isBlank() || answer.isNullOrBlank()) continue
                val source = try { row.getCell(6)?.toString()?.trim() } catch (_: Exception) { null }  // G列（选项，作来源）
                entries.add(KBEntry(question, answer, source ?: ""))
                imported++
            }
            stream.close()

            importedHashes.add(hash)
            Log.d("KnowledgeBase", "[$name] imported $imported entries, total=${entries.size}")
            imported
        } catch (e: Exception) {
            Log.e("KnowledgeBase", "[$name] import failed", e)
            -1
        }
    }

    fun search(query: String, topN: Int = 50): List<Pair<KBEntry, Float>> {
        if (entries.isEmpty()) return emptyList()

        // 规范化括号内空格，兼容不同数量的空格
        val normalizedQuery = query.replace(Regex("（\\s*）"), "（）")

        val exactMatches = entries.filter {
            val normalizedQuestion = it.question.replace(Regex("（\\s*）"), "（）")
            normalizedQuery.contains(normalizedQuestion)
        }
        if (exactMatches.isNotEmpty()) {
            return exactMatches.map { it to 1.0f }.take(topN)
        }

        // 无精确匹配，回退到 trigram Jaccard
        val qTrigrams = KBEntry.computeTrigrams(query)
        return entries.map { entry ->
            entry to KBEntry.jaccard(qTrigrams, entry.trigrams)
        }
        .filter { it.second > 0.15f }
        .sortedByDescending { it.second }
        .take(topN)
    }
}

object KnowledgeBaseManager {

    private val gson = Gson()
    private val kbs = mutableListOf<KnowledgeBase>()
    private var activeIndex = -1

    private lateinit var storageFile: File

    val activeKB: KnowledgeBase? get() = kbs.getOrNull(activeIndex)
    val allKBs: List<KnowledgeBase> get() = kbs
    val activeKBName: String get() = activeKB?.name ?: "无"

    fun init(context: Context) {
        storageFile = File(context.filesDir, "kb_data.json")
        load()
        // Default to 安规 knowledge base
        val anGuiIndex = kbs.indexOfFirst { it.name == "安规" }
        if (anGuiIndex >= 0) {
            activeIndex = anGuiIndex
            save()
        } else if (kbs.isEmpty()) {
            addKB("安规")
        }

        // Auto-import 安规题库 from assets (first launch only)
        val anGuiKb = kbs.firstOrNull { it.name == "安规" }
        if (anGuiKb != null && anGuiKb.entries.isEmpty()) {
            try {
                val dstFile = java.io.File(context.filesDir, "33-通信安规.xls")
                context.assets.open("33-通信安规.xls").use { input ->
                    dstFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                anGuiKb.importExcelWithDedup(dstFile.absolutePath)
                save()
                Log.d("KBManager", "Auto-imported 安规题库 from assets (${anGuiKb.entries.size} entries)")
            } catch (e: Exception) {
                Log.e("KBManager", "Auto-import 安规题库 failed", e)
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
