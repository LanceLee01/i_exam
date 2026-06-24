package com.examhelper.app.pipeline

import android.util.Log
import com.examhelper.app.util.ExtractedTextBus
import com.examhelper.app.util.ExtractedTextBus.SidebarState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class MultiRoundRunner(
    private val pipeline: SolvePipeline
) {
    companion object {
        private const val TAG = "MultiRoundRunner"
        private const val MAX_PAGES = 100
        private const val PAGE_WAIT_MS = 800L
        private const val SOLVE_TIMEOUT_MS = 60_000L
        private const val FILL_WAIT_MS = 1000L
    }

    private val _state = MutableStateFlow<MultiRoundState>(MultiRoundState.Idle)
    val state: StateFlow<MultiRoundState> = _state.asStateFlow()

    private var job: Job? = null
    private var cancelled = false
    private var cachedKbAnswerOptions: Map<Int, String> = emptyMap()
    private var cachedKbQuestionTexts: Map<Int, String> = emptyMap()
    private var cachedResolvedQuestions: Set<Int> = emptySet()

    sealed class MultiRoundState {
        data object Idle : MultiRoundState()
        data class Solving(val currentPage: Int, val totalPages: Int) : MultiRoundState()
        data class Filling(val currentPage: Int, val totalPages: Int, val answeredCount: Int) : MultiRoundState()
        data class Done(val totalAnswered: Int) : MultiRoundState()
        data class Error(val message: String) : MultiRoundState()
    }

    fun start(scope: CoroutineScope) {
        Log.e(TAG, "=== MultiRoundRunner.start() called (L1 ONLY mode) ===")
        cachedKbAnswerOptions = emptyMap()
        cachedKbQuestionTexts = emptyMap()
        cachedResolvedQuestions = emptySet()
        cancelled = false
        job = scope.launch(Dispatchers.Default) {
            Log.e(TAG, "=== MultiRound coroutine STARTED ===")
            try {
                var lastPage = -1
                var answeredCount = 0

                for (round in 1..MAX_PAGES) {
                    if (cancelled) return@launch

                    // 1. Read current page
                    Log.e(TAG, "--- Round $round: reading page ---")
                    val text = readCurrentPage()
                    val filtered = ScanPageFilter.filter(text)
                    val progress = ScanPageFilter.extractProgress(text)
                    val current = progress?.first ?: round
                    val total = progress?.second ?: -1
                    Log.e(TAG, "Round $round: page=$current/$total, filtered len=${filtered.length}")

                    // Stop condition: page is stuck
                    if (current == lastPage) { Log.w(TAG, "Page stuck at $current, stopping"); break }
                    lastPage = current

                    // Skip empty pages
                    if (filtered.isBlank()) {
                        Log.w(TAG, "Round $round: empty page $current, skipping")
                        if (current > 0 && total > 0 && current >= total) break
                        val clicked = clickNextPage()
                        if (!clicked) { Log.w(TAG, "No '下一页' at empty page $current, stopping"); break }
                        delay(PAGE_WAIT_MS)
                        continue
                    }

                    // 2. Solve current page — L1 only (no LLM)
                    Log.e(TAG, "Round $round: L1 matching page $current/$total")
                    _state.value = MultiRoundState.Solving(current, total)
                    updateSidebarMulti(SidebarState.MultiPhase.SOLVING, currentPage = current, totalPages = total)

                    val answer = solveL1CurrentPage(filtered)
                    if (cancelled) return@launch

                    if (answer.isEmpty()) {
                        val errMsg = "⚠️ 第 $current 页答题失败\nL1题库匹配未命中，请检查题库后重试"
                        Log.w(TAG, errMsg)
                        updateSidebarMulti(SidebarState.MultiPhase.ERROR, errorMessage = errMsg)
                        _state.value = MultiRoundState.Error(errMsg)
                        return@launch
                    }
                    Log.e(TAG, "Round $round: L1 match done, answer length=${answer.length}")

                    // 3. Fill answers on current page
                    answeredCount++
                    val qSummary = buildQuestionSummary(filtered, answer, cachedKbQuestionTexts)
                    Log.e(TAG, "qSummary=[$qSummary]")
                    Log.e(TAG, "Round $round: filling page $current/$total")
                    _state.value = MultiRoundState.Filling(current, total, answeredCount)
                    updateSidebarMulti(SidebarState.MultiPhase.FILLING,
                        currentPage = current, totalPages = total, answeredCount = answeredCount,
                        currentQuestionSummary = qSummary)

                    // 点击填入 — 传递 kbAnswerOptions 用于选项文字匹配
                    clickAnswer(answer, filtered, cachedKbAnswerOptions)
                    delay(FILL_WAIT_MS)
                    Log.e(TAG, "Round $round: filled page $current, total answered=$answeredCount")

                    // 4. Check if done
                    if (current > 0 && total > 0 && current >= total) {
                        Log.e(TAG, "Last page reached: $current/$total")
                        break
                    }

                    // 5. Next page
                    val clicked = clickNextPage()
                    if (!clicked) { Log.w(TAG, "No '下一页' at page $current, stopping"); break }
                    delay(PAGE_WAIT_MS)
                }

                _state.value = MultiRoundState.Done(answeredCount)
                updateSidebarMulti(SidebarState.MultiPhase.DONE,
                    totalPages = lastPage, answeredCount = answeredCount)
                Log.e(TAG, "MultiRound done: $answeredCount pages answered")
            } catch (e: CancellationException) {
                Log.d(TAG, "Cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "MultiRound failed", e)
                val errMsg = "多轮答题异常: ${e.message ?: "未知错误"}"
                updateSidebarMulti(SidebarState.MultiPhase.ERROR, errorMessage = errMsg)
                _state.value = MultiRoundState.Error(errMsg)
            }
        }
    }

    fun cancel() {
        cancelled = true
        job?.cancel()
    }

    // ── L1-only solve for current page ──

    private suspend fun solveL1CurrentPage(text: String): String {
        pipeline.solveL1Only(text)

        // Wait for Done or Error state
        val deadline = System.currentTimeMillis() + SOLVE_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline && !cancelled) {
            delay(100)
            val state = ExtractedTextBus.sidebarState.value
            when (state) {
                is SidebarState.Done -> {
                    cachedKbAnswerOptions = state.kbAnswerOptions
                    cachedKbQuestionTexts = state.kbQuestionTexts
                    cachedResolvedQuestions = state.resolvedQuestions
                    return state.answer
                }
                is SidebarState.Error -> {
                    Log.w(TAG, "solveL1CurrentPage: error from solveL1Only: ${state.message}")
                    return ""
                }
                else -> { /* still waiting */ }
            }
        }
        Log.w(TAG, "solveL1CurrentPage timed out")
        return ""
    }

    // ── Page navigation via ExtractedTextBus ──

    private suspend fun readCurrentPage(): String = withContext(Dispatchers.Default) {
        var result = ""
        ExtractedTextBus.sendEvent(ExtractedTextBus.Event.RequestExtract)

        val deadline = System.currentTimeMillis() + 10_000
        while (result.isEmpty() && System.currentTimeMillis() < deadline) {
            delay(100)
            val state = ExtractedTextBus.sidebarState.value
            if (state is ExtractedTextBus.SidebarState.Preview) {
                result = state.text
            }
        }
        if (result.isEmpty()) Log.w(TAG, "readCurrentPage timed out")
        result
    }

    private suspend fun clickNextPage(): Boolean = withContext(Dispatchers.Main) {
        Log.d(TAG, "clickNextPage: sending ClickPage event")
        ExtractedTextBus.sendEvent(ExtractedTextBus.Event.ClickPage("下一页"))
        delay(600)
        true
    }

    private suspend fun clickAnswer(answer: String, sourceText: String, kbAnswerOptions: Map<Int, String>) {
        ExtractedTextBus.sendEvent(ExtractedTextBus.Event.ClickAnswer(answer, sourceText, kbAnswerOptions, cachedResolvedQuestions))
        // Wait for auto-click to finish
        val answerCount = answer.lines().size.coerceAtLeast(1)
        delay(1500L * answerCount + 2000L)
    }

    // ── Helpers ──

    /** Build a short summary of the current question + answer for UI display.
     *  Uses KB original data (question text + options) when available. */
    private fun buildQuestionSummary(filtered: String, answer: String, kbQuestionTexts: Map<Int, String>): String {
        // Parse first question number from filtered text
        val qPattern = Regex("""(\d+)[、.]""")
        val firstQMatch = qPattern.find(filtered)
        val firstQNum = firstQMatch?.groupValues?.get(1)?.toIntOrNull()

        // Check for KB original data
        if (firstQNum != null && firstQNum in kbQuestionTexts) {
            val kbQuestion = kbQuestionTexts[firstQNum] ?: ""
            val kbOptions = cachedKbAnswerOptions[firstQNum] ?: ""
            // Parse answer line for this question
            val ansLine = answer.lines().firstOrNull {
                it.contains("[${firstQNum}]") || it.contains("$firstQNum]") || it.contains("[$firstQNum")
            }?.trim()?.take(60) ?: ""
            return buildString {
                append("📝 $kbQuestion")
                if (kbOptions.isNotBlank()) append("\n📋 $kbOptions")
                if (ansLine.isNotBlank()) append("\n✅ $ansLine")
            }
        }

        // Fallback: extract from filtered exam text
        val lines = filtered.lines().map { it.trim() }.filter { it.isNotBlank() }
        val stopWords = setOf("上一页", "下一页", "开始考试", "提交答案")
        val optionPattern = Regex("""^[A-F]\s*[.、:：)）]""")
        val stemLines = lines
            .dropWhile {
                it.startsWith("单选题") || it.startsWith("多选题") || it.startsWith("判断题") ||
                Regex("""^\d+[、.]""").matches(it) ||
                Regex("""^\d+-\S+""").matches(it)
            }
            .takeWhile { !optionPattern.containsMatchIn(it) && it !in stopWords && it != "正确" && it != "错误" }
        val stem = stemLines.joinToString(" ").take(100)
            .ifBlank { filtered.lines().firstOrNull()?.take(60) ?: filtered.take(40) }
        val optionLines = lines
            .filter { Regex("""^[A-F]\s*[.、:：)）]""").containsMatchIn(it) || it == "正确" || it == "错误" }
        val ansLine = answer.lines().firstOrNull { Regex("""[\[【]?\d+[\]】]?""").containsMatchIn(it) }?.trim()?.take(30) ?: ""
        return buildString {
            append("📝 $stem")
            if (optionLines.isNotEmpty()) append("\n📋 ${optionLines.joinToString("  ").take(80)}")
            if (ansLine.isNotBlank()) append("\n✅ $ansLine")
        }
    }

    private fun updateSidebarMulti(
        phase: SidebarState.MultiPhase,
        currentPage: Int = 0,
        totalPages: Int = 0,
        progress: Float = 0f,
        answeredCount: Int = 0,
        errorMessage: String = "",
        currentQuestionSummary: String = ""
    ) {
        ExtractedTextBus.updateSidebarState(
            SidebarState.MultiRound(
                phase = phase,
                currentPage = currentPage,
                totalPages = totalPages,
                progress = progress,
                answeredCount = answeredCount,
                errorMessage = errorMessage,
                currentQuestionSummary = currentQuestionSummary
            )
        )
    }
}
