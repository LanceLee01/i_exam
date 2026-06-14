package com.examhelper.app.knowledge

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.File
import java.io.FileInputStream
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
    }
}

data class KnowledgeBase(
    val id: String = UUID.randomUUID().toString().take(8),
    val name: String = ""
) {
    val entries = mutableListOf<KBEntry>()
    val count: Int get() = entries.size

    fun importExcel(path: String): Int {
        return try {
            val stream = FileInputStream(path)
            val workbook = WorkbookFactory.create(stream)
            val sheet = workbook.getSheetAt(0)
            var imported = 0
            for (row in sheet) {
                if (row.rowNum == 0) continue
                val question = row.getCell(0)?.toString()?.trim() ?: continue
                val answer = row.getCell(1)?.toString()?.trim()
                if (question.isBlank() || answer.isNullOrBlank()) continue
                val source = try { row.getCell(2)?.toString()?.trim() } catch (_: Exception) { null }
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

    fun search(query: String, topN: Int = 5): List<Pair<KBEntry, Float>> {
        if (entries.isEmpty()) return emptyList()
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
            val data = KBStorageData(kbs.toList(), activeIndex)
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
            kbs.addAll(data.kbs)
            activeIndex = data.activeKbIndex.coerceIn(-1, kbs.size - 1)
            Log.d("KBManager", "Loaded ${kbs.size} KBs, active=$activeIndex")
        } catch (e: Exception) {
            Log.e("KBManager", "load failed", e)
        }
    }
}

private data class KBStorageData(
    val kbs: List<KnowledgeBase>,
    val activeKbIndex: Int
)
