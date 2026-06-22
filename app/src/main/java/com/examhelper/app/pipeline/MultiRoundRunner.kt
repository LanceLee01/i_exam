package com.examhelper.app.pipeline

import android.util.Log
import com.examhelper.app.service.PageNavigator
import com.examhelper.app.util.ExtractedTextBus
import com.examhelper.app.util.ExtractedTextBus.SidebarState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class MultiRoundRunner(
    private val pipeline: SolvePipeline
) {
    private val pageNavigator = PageNavigator()
    companion object {
        private const val TAG = "MultiRoundRunner"
        private const val MAX_PAGES = 100
        private const val PAGE_WAIT_MS = 600L
        private const val SOLVE_TIMEOUT_MS = 300_000L
    }

    private val _state = MutableStateFlow<MultiRoundState>(MultiRoundState.Idle)
    val state: StateFlow<MultiRoundState> = _state.asStateFlow()

    private var job: Job? = null
    private var cancelled = false

    sealed class MultiRoundState {
        data object Idle : MultiRoundState()
        data class Solving(val currentPage: Int, val totalPages: Int) : MultiRoundState()
        data class Filling(val currentPage: Int, val totalPages: Int, val answeredCount: Int) : MultiRoundState()
        data class Done(val totalAnswered: Int) : MultiRoundState()
        data class Error(val message: String) : MultiRoundState()
    }

    fun start(scope: CoroutineScope) {
        Log.e(TAG, "=== MultiRoundRunner.start() called ===")
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
                    val text = pageNavigator.readCurrentPage()
                    val filtered = ScanPageFilter.filter(text)
                    val progress = ScanPageFilter.extractProgress(text)
                    val current = progress?.first ?: round
                    val total = progress?.second ?: -1
                    Log.e(TAG, "Round $round: page=$current/$total, filtered len=${filtered.length}")

                    // Stop conditions
                    if (current == lastPage) { Log.w(TAG, "Page stuck at $current, stopping"); break }
                    lastPage = current

                    // Skip empty pages
                    if (filtered.isBlank()) {
                        Log.w(TAG, "Round $round: empty page $current, skipping")
                        if (current > 0 && total > 0 && current >= total) break
                        val clicked = pageNavigator.clickNextPage()
                        if (!clicked) { Log.w(TAG, "No '下一页' at empty page $current, stopping"); break }
                        delay(PAGE_WAIT_MS)
                        continue
                    }

                    // 2. Solve current page
                    Log.e(TAG, "Round $round: solving page $current/$total")
                    _state.value = MultiRoundState.Solving(current, total)
                    updateSidebarMulti(SidebarState.MultiPhase.SOLVING, currentPage = current, totalPages = total)

                    val answer = solveCurrentPage(filtered)
                    if (cancelled) return@launch
                    if (answer.isEmpty()) {
                        Log.w(TAG, "Round $round: solve returned empty for page $current")
                        updateSidebarMulti(SidebarState.MultiPhase.ERROR, errorMessage = "解答失败")
                        return@launch
                    }
                    Log.e(TAG, "Round $round: solve done, answer length=${answer.length}")

                    // 3. Fill answers on current page
                    answeredCount++
                    // 生成题目摘要：过滤后的题目文本（截断） + 答案
                    val qSummary = buildQuestionSummary(filtered, answer)
                    Log.e(TAG, "qSummary=[$qSummary]")
                    Log.e(TAG, "Round $round: filling page $current/$total")
                    _state.value = MultiRoundState.Filling(current, total, answeredCount)
                    updateSidebarMulti(SidebarState.MultiPhase.FILLING,
                        currentPage = current, totalPages = total, answeredCount = answeredCount,
                        currentQuestionSummary = qSummary)

                    pageNavigator.clickAnswer(answer, filtered)
                    Log.e(TAG, "Round $round: filled page $current, total answered=$answeredCount")

                    // 4. Check if done
                    if (current > 0 && total > 0 && current >= total) {
                        Log.e(TAG, "Last page reached: $current/$total")
                        break
                    }

                    // 5. Next page
                    val clicked = pageNavigator.clickNextPage()
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
                updateSidebarMulti(SidebarState.MultiPhase.ERROR, errorMessage = e.message ?: "未知错误")
            }
        }
    }

    fun cancel() {
        cancelled = true
        job?.cancel()
    }

    // ── Solve single page ──

    private suspend fun solveCurrentPage(text: String): String {
        // Check if already in Done state (fast path for L1-only solves)
        var current = ExtractedTextBus.sidebarState.value
        if (current is SidebarState.Done) {
            return current.answer
        }

        pipeline.solve(text)

        var answer = ""
        var done = false
        val collectionJob = CoroutineScope(Dispatchers.Default).launch {
            ExtractedTextBus.sidebarState.collect { s ->
                if (s is SidebarState.Done && !done) {
                    answer = s.answer
                    done = true
                }
            }
        }
        val deadline = System.currentTimeMillis() + SOLVE_TIMEOUT_MS
        while (!done && !cancelled && System.currentTimeMillis() < deadline) {
            delay(200)
        }
        collectionJob.cancel()
        return answer
    }

    // ── Helpers ──

    /** 从过滤后的题目文本和答案构造摘要，在 FILLING 阶段显示：题干 + 选项 + 答案 */
    private fun buildQuestionSummary(filtered: String, answer: String): String {
        val lines = filtered.lines().map { it.trim() }.filter { it.isNotBlank() }
        // 题干：跳过标题行、题型标签、题号行，停在选项/判断题答案/翻页文字之前，排除"正确""错误"等选项文字混入
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
        // 选项：A. B. C. D. + 判断题正确/错误
        val optionLines = lines
            .filter { Regex("""^[A-F]\s*[.、:：)）]""").containsMatchIn(it) || it == "正确" || it == "错误" }
        // 答案：解析格式 "[N] A B" 提取字母或判断结果
        val ansLine = answer.lines().firstOrNull { Regex("""[\[【]?\d+[\]】]?""").containsMatchIn(it) }?.trim()?.take(30) ?: ""
        // 排版
        val result = buildString {
            append("📝 $stem")
            if (optionLines.isNotEmpty()) append("\n📋 ${optionLines.joinToString("  ").take(80)}")
            if (ansLine.isNotBlank()) append("\n✅ $ansLine")
        }
        Log.e(TAG, "buildQuestionSummary result: [$result]")
        return result
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
