package com.examhelper.app.pipeline

import android.content.Context
import android.util.Log
import com.examhelper.app.knowledge.KBEngine
import com.examhelper.app.knowledge.KBEntry
import com.examhelper.app.knowledge.KnowledgeBaseManager
import com.examhelper.app.util.ExtractedTextBus
import com.examhelper.app.util.ExtractedTextBus.AnswerSource
import com.examhelper.app.util.ExtractedTextBus.SidebarState

class SolvePipeline(private val context: Context) {

    private val kbEngine = KBEngine(context)

    suspend fun solve(text: String) {
        // L1: Excel 题库精准匹配
        val excelHits = KnowledgeBaseManager.activeKB?.search(text, topN = 5) ?: emptyList()
        val excelDirectHit = excelHits.firstOrNull()?.takeIf { (_, score) -> score >= 0.70f }

        if (excelDirectHit != null) {
            val (entry, _) = excelDirectHit
            Log.d(TAG, "L1 Excel match: ${entry.answer.take(50)}")
            ExtractedTextBus.updateSidebarState(
                SidebarState.Done(text, entry.answer, AnswerSource.EXCEL_MATCH)
            )
            return
        }

        // L2: Wiki 知识库检索
        val wikiResult = kbEngine.searchByQuestion(text)
        val combinedPages = (wikiResult.ftsPages + wikiResult.trigramPages).distinctBy { it.id }
        val wikiTopScore = combinedPages.maxOfOrNull { page ->
            val pTri = KBEntry.computeTrigrams(page.title + page.summary.take(200))
            val qTri = KBEntry.computeTrigrams(text)
            KBEntry.jaccard(qTri, pTri)
        } ?: 0f

        if (wikiTopScore >= 0.50f && combinedPages.isNotEmpty()) {
            // 直接从匹配度最高的页面提取答案文本（不调用LLM）
            val bestPage = combinedPages.maxByOrNull { page ->
                val pTri = KBEntry.computeTrigrams(page.title + page.summary.take(200))
                val qTri = KBEntry.computeTrigrams(text)
                KBEntry.jaccard(qTri, pTri)
            }
            val answer = if (bestPage != null) {
                "【${bestPage.title}】${bestPage.summary}\n${bestPage.content.take(500)}"
            } else {
                ""
            }
            Log.d(TAG, "L2 Wiki match: score=$wikiTopScore pages=${combinedPages.size}")
            ExtractedTextBus.updateSidebarState(
                SidebarState.Done(text, answer, AnswerSource.KB_MATCH)
            )
            return
        }

        // 未命中：返回错误提示
        ExtractedTextBus.updateSidebarState(
            SidebarState.Error("题库中未找到匹配答案，请检查题库是否包含此题目")
        )
    }

    /** 仅使用 L1 题库匹配（不调用任何网络服务），供多轮答题使用 */
    suspend fun solveL1Only(text: String) {
        try {
            val excelHits = KnowledgeBaseManager.activeKB?.search(text, topN = 5) ?: emptyList()
            val directHit = excelHits.firstOrNull()?.takeIf { (_, score) -> score >= 0.70f }

            if (directHit != null) {
                val (entry, _) = directHit
                Log.d(TAG, "L1 match: ${entry.answer.take(50)}")
                ExtractedTextBus.updateSidebarState(
                    SidebarState.Done(text, entry.answer, AnswerSource.EXCEL_MATCH)
                )
            } else {
                // 收集模糊匹配作为提示
                val hint = excelHits.firstOrNull()?.let { (e, score) ->
                    "最接近匹配(=${"%.0f%%".format(score * 100)}): ${e.question.take(40)}"
                } ?: "题库中未找到匹配"
                ExtractedTextBus.updateSidebarState(
                    SidebarState.Error(hint)
                )
            }
        } catch (e: Exception) {
            ExtractedTextBus.updateSidebarState(
                SidebarState.Error("匹配异常: ${e.message}")
            )
        }
    }

    companion object {
        private const val TAG = "SolvePipeline"
    }
}
