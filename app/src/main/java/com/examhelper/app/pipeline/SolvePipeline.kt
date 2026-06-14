package com.examhelper.app.pipeline

import android.content.Context
import android.util.Log
import com.examhelper.app.ExamApplication
import com.examhelper.app.knowledge.KBEngine
import com.examhelper.app.knowledge.KBEntry
import com.examhelper.app.knowledge.KnowledgeBaseManager
import com.examhelper.app.network.LLMClient
import com.examhelper.app.util.ExtractedTextBus
import com.examhelper.app.util.ExtractedTextBus.AnswerSource
import com.examhelper.app.util.ExtractedTextBus.SidebarState

class SolvePipeline(private val context: Context) {

    private val kbEngine = KBEngine(context)

    suspend fun solve(text: String) {
        val requestStartMs = System.currentTimeMillis()
        val config = ExamApplication.instance.appConfig.getSnapshot()
        val maxTokens = config.maxTokens
        val userMessage = "以下是考试界面提取的文字，请根据内容答题：\n\n$text"

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
            val answer = kbEngine.getAnswerFromKB(text, combinedPages) ?: ""
            Log.d(TAG, "L2 Wiki match: score=$wikiTopScore pages=${combinedPages.size}")
            ExtractedTextBus.updateSidebarState(
                SidebarState.Done(text, answer, AnswerSource.KB_MATCH)
            )
            return
        }

        // Build context for LLM
        var llmSource = AnswerSource.LLM_DIRECT
        var effectiveMessage = userMessage

        val excelHints = excelHits.filter { (_, score) -> score >= 0.40f }
        val wikiHints = if (wikiTopScore >= 0.20f) combinedPages else emptyList()

        if (excelHints.isNotEmpty() || wikiHints.isNotEmpty()) {
            val parts = mutableListOf<String>()
            llmSource = AnswerSource.KB_INFER

            if (excelHints.isNotEmpty()) {
                val excelCtx = excelHints.joinToString("\n") { (e, _) ->
                    "题目: ${e.question}\n答案: ${e.answer}"
                }
                parts.add("以下是题库中匹配的题目和答案，请优先参考：\n\n$excelCtx")
            }

            if (wikiHints.isNotEmpty()) {
                val wikiCtx = wikiHints.joinToString("\n\n") { page ->
                    "【${page.title}】\n${page.summary}\n${page.content.take(300)}"
                }
                parts.add("以下是知识库中的相关知识点，请参考后作答：\n\n$wikiCtx")
            }

            effectiveMessage = parts.joinToString("\n\n") + "\n\n$userMessage"
        }

        // L3/L4: LLM 答题
        ExtractedTextBus.updateSidebarState(
            SidebarState.Loading("正在调用 LLM 解答...", requestStartMs, maxTokens)
        )
        try {
            val client = LLMClient()
            val textLen = effectiveMessage.length
            val estTokens = (textLen / 1.5).toInt().coerceAtLeast(50)
            ExtractedTextBus.lastPromptTokens = estTokens
            val accumulated = StringBuilder()
            val estimatedTotalTokens = maxTokens.coerceAtLeast(1)
            var firstChunk = true
            var streamStartMs = 0L
            client.chatStream(
                endpoint = config.apiEndpoint,
                apiKey = config.apiKey,
                model = config.modelName,
                temperature = config.temperature,
                maxTokens = maxTokens,
                systemPrompt = config.systemPrompt,
                userMessage = effectiveMessage
            ).collect { chunk ->
                if (firstChunk) {
                    firstChunk = false
                    streamStartMs = System.currentTimeMillis()
                    ExtractedTextBus.lastTtftMs = streamStartMs - requestStartMs
                }
                accumulated.append(chunk)
                val roughTokenEstimate = accumulated.length * 2
                val progress = (roughTokenEstimate.toFloat() / estimatedTotalTokens)
                    .coerceIn(0f, 0.95f)
                val elapsed = ((System.currentTimeMillis() - streamStartMs) / 1000).toInt() + 1
                val speed = roughTokenEstimate.toFloat() / elapsed
                if (speed > 0) ExtractedTextBus.lastTokensPerSec = speed
                ExtractedTextBus.updateSidebarState(
                    SidebarState.Streaming(text, accumulated.toString(), progress, requestStartMs, maxTokens)
                )
            }
            val finalAnswer = if (accumulated.isEmpty()) client.reasoningBuffer.toString() else accumulated.toString()
            ExtractedTextBus.updateSidebarState(
                SidebarState.Done(text, finalAnswer, llmSource)
            )
        } catch (e: Exception) {
            ExtractedTextBus.updateSidebarState(
                SidebarState.Error("请求异常: ${e.message}")
            )
        }
    }

    companion object {
        private const val TAG = "SolvePipeline"
    }
}
