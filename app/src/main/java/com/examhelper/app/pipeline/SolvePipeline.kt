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
            Log.d(TAG, "solveL1Only() ENTER — text length=${text.length}")
            val startMs = System.currentTimeMillis()

            // Extract all question numbers from the page text
            val qRegex = Regex("""^\s*(\d+)\s*[、.]""", RegexOption.MULTILINE)
            val allQ = qRegex.findAll(text).mapNotNull { it.groupValues[1].toIntOrNull() }.toSet().sorted()

            if (allQ.isEmpty()) {
                Log.w(TAG, "solveL1Only: no question numbers found in text (len=${text.length})")
                ExtractedTextBus.updateSidebarState(
                    SidebarState.Error("未识别到题号，请确认已正确读取考试页面")
                )
                return
            }

            // For each question, run L1 search using a per-question snippet to maximize match accuracy
            val l1Answers = linkedMapOf<Int, String>()
            val kbQuestionTexts = linkedMapOf<Int, String>()
            val kbAnswerOptions = linkedMapOf<Int, String>()
            val kbOriginalAnswers = linkedMapOf<Int, String>()
            val resolvedQuestions = mutableSetOf<Int>()
            val unmatched = mutableListOf<Int>()

            for (qNum in allQ) {
                val qSnippet = try {
                    extractSnippetForQuestion(text, qNum)
                } catch (e: Exception) {
                    Log.w(TAG, "extractSnippetForQuestion Q$qNum failed: ${e.message}")
                    text
                }
                val hits = try {
                    KnowledgeBaseManager.activeKB?.search(qSnippet, topN = 3) ?: emptyList()
                } catch (e: Exception) {
                    Log.w(TAG, "KnowledgeBase.search Q$qNum failed: ${e.javaClass.simpleName}: ${e.message}", e)
                    emptyList()
                }
                val directHit = hits.firstOrNull()?.takeIf { (_, score) -> score >= 0.70f }
                if (directHit != null) {
                    val (entry, _) = directHit
                    try {
                        l1Answers[qNum] = entry.answer
                        kbQuestionTexts[qNum] = entry.question
                        if (entry.options.isNotBlank()) kbAnswerOptions[qNum] = entry.options
                        kbOriginalAnswers[qNum] = entry.answer
                        resolvedQuestions.add(qNum)
                        Log.d(TAG, "solveL1Only Q$qNum hit: kbQ='${entry.question.take(30)}' answer='${entry.answer.take(20)}'")
                    } catch (e: Exception) {
                        Log.w(TAG, "solveL1Only Q$qNum entry access failed: ${e.javaClass.simpleName}: ${e.message}", e)
                        unmatched.add(qNum)
                    }
                } else {
                    val best = hits.firstOrNull()
                    Log.w(TAG, "solveL1Only Q$qNum NO HIT (best=${best?.let { "%.2f".format(it.second) } ?: "n/a"})")
                    unmatched.add(qNum)
                }
            }

            val elapsed = System.currentTimeMillis() - startMs
            Log.d(TAG, "solveL1Only: matched ${l1Answers.size}/${allQ.size} in ${elapsed}ms; kbOptions=${kbAnswerOptions.size}")

            if (unmatched.isNotEmpty()) {
                val errorMsg = buildString {
                    append("⚠️ 题库匹配失败\n")
                    append("共 ${allQ.size} 题，未匹配 ${unmatched.size} 题")
                    if (unmatched.isNotEmpty()) append(": ${formatRanges(unmatched)}")
                    append("\n请检查题库后重试")
                }
                Log.w(TAG, "solveL1Only: unmatched questions: $unmatched")
                ExtractedTextBus.updateSidebarState(SidebarState.Error(errorMsg))
                return
            }

            // Format answer as multi-line "[q] X" expected by parseAnswerPairs()
            val answerText = l1Answers.entries.joinToString("\n") { (q, a) -> "[$q] $a" }

            // Build a display summary so the user can read the KB question + options + screen resolution
            val firstQNum = allQ.firstOrNull()
            val qSummary = if (firstQNum != null) {
                buildQuestionSummaryForUI(text, answerText, kbQuestionTexts, kbAnswerOptions, kbOriginalAnswers)
            } else ""

            ExtractedTextBus.updateSidebarState(
                SidebarState.Done(
                    text = text,
                    answer = answerText,
                    source = AnswerSource.EXCEL_MATCH,
                    questionSources = emptyMap(),
                    kbAnswerOptions = kbAnswerOptions,
                    kbQuestionTexts = kbQuestionTexts,
                    toggleFailedQuestions = emptyList(),
                    resolvedQuestions = emptySet(),
                    kbOriginalAnswers = kbOriginalAnswers,
                    currentQuestionSummary = qSummary
                )
            )
            Log.d(TAG, "solveL1Only: emitted Done with ${l1Answers.size} answers, kbOptions=${kbAnswerOptions.size} (resolvedQuestions intentionally empty — let performAutoClick do option-text resolution)")
        } catch (e: Exception) {
            Log.e(TAG, "solveL1Only exception: ${e.javaClass.simpleName}: ${e.message}", e)
            ExtractedTextBus.updateSidebarState(
                SidebarState.Error("匹配异常: ${e.javaClass.simpleName}: ${e.message}")
            )
        }
    }

    /**
     * Extract a self-contained snippet for a single question: from its number
     * up to the next question number (or end). This avoids ambiguity from
     * matching the whole page against a single KB entry.
     */
    private fun extractSnippetForQuestion(text: String, qNum: Int): String {
        val lines = text.lines()
        val qPattern = Regex("""^\s*(\d+)\s*[、.]""")
        val startIdx = lines.indexOfFirst { line ->
            val m = qPattern.find(line.trim())
            m != null && m.groupValues[1].toIntOrNull() == qNum
        }
        if (startIdx < 0) return text
        val endIdx = (startIdx + 1 until lines.size).firstOrNull { i ->
            val m = qPattern.find(lines[i].trim())
            m != null && m.groupValues[1].toIntOrNull() != qNum
        } ?: lines.size
        return lines.subList(startIdx, endIdx).joinToString("\n")
    }

    /** Format a list of integers into compact ranges, e.g. [1,2,3,5,7] -> "1-3, 5, 7" */
    private fun formatRanges(nums: List<Int>): String {
        if (nums.isEmpty()) return ""
        val sorted = nums.sorted()
        val sb = StringBuilder()
        var rangeStart = sorted[0]
        var prev = sorted[0]
        for (i in 1 until sorted.size) {
            val cur = sorted[i]
            if (cur == prev + 1) { prev = cur; continue }
            if (prev == rangeStart) sb.append(rangeStart)
            else sb.append("$rangeStart-$prev")
            sb.append(", ")
            rangeStart = cur; prev = cur
        }
        if (prev == rangeStart) sb.append(rangeStart) else sb.append("$rangeStart-$prev")
        return sb.toString()
    }

    companion object {
        private const val TAG = "SolvePipeline"
    }
}
