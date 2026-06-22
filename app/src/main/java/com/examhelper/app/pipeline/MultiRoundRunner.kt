package com.examhelper.app.pipeline

import android.util.Log
import com.examhelper.app.service.PageNavigator
import com.examhelper.app.service.ExamAccessibilityService
import com.examhelper.app.util.ExtractedTextBus
import com.examhelper.app.util.ExtractedTextBus.SidebarState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class MultiRoundRunner(
    private val pipeline: SolvePipeline,
    private val service: ExamAccessibilityService
) {
    private val pageNavigator: PageNavigator
        get() = PageNavigator(service)
    companion object {
        private const val TAG = "MultiRoundRunner"
        private const val MAX_PAGES = 100
        private const val PAGE_WAIT_MS = 600L
        private const val MAX_CONSECUTIVE_EMPTY = 3
        private const val SOLVE_TIMEOUT_MS = 300_000L
    }

    private val _state = MutableStateFlow<MultiRoundState>(MultiRoundState.Idle)
    val state: StateFlow<MultiRoundState> = _state.asStateFlow()

    private var job: Job? = null
    private var cancelled = false

    sealed class MultiRoundState {
        data object Idle : MultiRoundState()
        data class Scanning(val currentPage: Int, val totalPages: Int) : MultiRoundState()
        data class Solving(val progress: Float, val etaSec: Int) : MultiRoundState()
        data class Filling(val currentPage: Int, val totalPages: Int, val answeredCount: Int) : MultiRoundState()
        data class Done(val totalAnswered: Int) : MultiRoundState()
        data class Error(val message: String) : MultiRoundState()
    }

    fun start(scope: CoroutineScope) {
        cancelled = false
        job = scope.launch(Dispatchers.Default) {
            try {
                // Phase 1: Scan all pages
                val allPages = scanAllPages()
                if (cancelled) return@launch
                if (allPages.isEmpty()) {
                    updateSidebarError("未读取到任何题目")
                    return@launch
                }
                Log.d(TAG, "Scanned ${allPages.size} pages")

                // Phase 2: Solve combined text
                val combined = combinePages(allPages)
                Log.d(TAG, "Combined text length=${combined.length}, starting solve")
                val answer = solveAll(combined)
                if (cancelled) return@launch
                if (answer.isEmpty()) {
                    updateSidebarError("解答失败，请重试")
                    return@launch
                }

                // Phase 3: Fill answers page by page
                fillAllPages(answer, allPages.size)
            } catch (e: CancellationException) {
                Log.d(TAG, "Cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "MultiRound failed", e)
                updateSidebarError(e.message ?: "未知错误")
            }
        }
    }

    fun cancel() {
        cancelled = true
        job?.cancel()
    }

    // ── Phase 1: Scan ──

    private suspend fun scanAllPages(): Map<Int, String> {
        val pages = mutableMapOf<Int, String>()
        var consecutiveEmpty = 0
        var lastPage = -1

        for (round in 1..MAX_PAGES) {
            if (cancelled) return emptyMap()

            val text = pageNavigator.readCurrentPage()
            val filtered = ScanPageFilter.filter(text)
            val progress = ScanPageFilter.extractProgress(text)
            val current = progress?.first ?: round
            val total = progress?.second ?: -1

            if (filtered.isBlank()) {
                consecutiveEmpty++
                if (consecutiveEmpty >= MAX_CONSECUTIVE_EMPTY) {
                    Log.w(TAG, "Stopping scan: $MAX_CONSECUTIVE_EMPTY consecutive empty pages")
                    break
                }
            } else {
                consecutiveEmpty = 0
                pages[current] = filtered
            }

            _state.value = MultiRoundState.Scanning(current, total)
            updateSidebarMulti(SidebarState.MultiPhase.SCANNING, currentPage = current, totalPages = total)

            // Stop conditions
            if (current == lastPage) { Log.w(TAG, "Page stuck at $current, stopping scan"); break }
            lastPage = current
            if (current > 0 && total > 0 && current >= total) break

            // Next page
            val clicked = pageNavigator.clickNextPage()
            if (!clicked) { Log.d(TAG, "No '下一页' button at page $current, stopping scan"); break }
            delay(PAGE_WAIT_MS)
        }
        Log.d(TAG, "Scan done: ${pages.size} pages, keys=[${pages.keys.sorted()}]")
        return pages
    }

    private fun combinePages(pages: Map<Int, String>): String {
        return pages.entries.sortedBy { it.key }.joinToString("\n\n") { (pageNum, text) ->
            text
        }
    }

    // ── Phase 2: Solve ──

    private suspend fun solveAll(combinedText: String): String {
        _state.value = MultiRoundState.Solving(0f, 0)
        updateSidebarMulti(SidebarState.MultiPhase.SOLVING, progress = 0f)

        // Launch pipeline.solve() — it updates sidebarState internally
        pipeline.solve(combinedText)

        // Wait for pipeline to produce a Done state
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
        if (cancelled) return ""
        if (!done) {
            Log.w(TAG, "Solve timed out after ${SOLVE_TIMEOUT_MS}ms")
            return ""
        }
        Log.d(TAG, "Solve done: answer length=${answer.length}")
        return answer
    }

    // ── Phase 3: Fill ──

    private suspend fun fillAllPages(answer: String, totalPages: Int) {
        Log.d(TAG, "Filling: navigating back to page 1...")
        pageNavigator.navigateToFirstPage()
        delay(500)

        val pairs = parseAnswerPairs(answer)
        Log.d(TAG, "Filling: ${pairs.size} answers for $totalPages pages")

        updateSidebarMulti(SidebarState.MultiPhase.FILLING, currentPage = 1, totalPages = totalPages)

        for (qNum in 1..totalPages) {
            if (cancelled) return

            _state.value = MultiRoundState.Filling(qNum, totalPages, qNum - 1)
            updateSidebarMulti(SidebarState.MultiPhase.FILLING,
                currentPage = qNum, totalPages = totalPages, answeredCount = qNum - 1)

            val current = pairs.firstOrNull { it.first == qNum }
            if (current != null) {
                val line = "[${qNum}] ${current.second.joinToString(" ")}"
                Log.d(TAG, "Filling Q$qNum: $line")
                pageNavigator.clickAnswer(line, "")
                delay(200)
            } else {
                Log.w(TAG, "Filling Q$qNum: no answer, skipping")
            }

            // Last page — don't navigate
            if (qNum >= totalPages) break

            val clicked = pageNavigator.clickNextPage()
            if (!clicked) {
                Log.w(TAG, "Filling: no '下一页' at page $qNum, stopping")
                break
            }
            delay(PAGE_WAIT_MS)
        }

        _state.value = MultiRoundState.Done(totalPages)
        updateSidebarMulti(SidebarState.MultiPhase.DONE,
            totalPages = totalPages, answeredCount = totalPages)
        Log.d(TAG, "Fill done: $totalPages pages")
    }

    // ── Helpers ──

    private fun updateSidebarMulti(
        phase: SidebarState.MultiPhase,
        currentPage: Int = 0,
        totalPages: Int = 0,
        progress: Float = 0f,
        answeredCount: Int = 0,
        message: String = ""
    ) {
        ExtractedTextBus.updateSidebarState(
            SidebarState.MultiRound(phase, currentPage, totalPages, progress, answeredCount, message)
        )
    }

    private fun updateSidebarError(msg: String) {
        _state.value = MultiRoundState.Error(msg)
        ExtractedTextBus.updateSidebarState(
            SidebarState.MultiRound(SidebarState.MultiPhase.ERROR, errorMessage = msg)
        )
    }

    private fun parseAnswerPairs(answer: String): List<Pair<Int, List<String>>> {
        val result = mutableListOf<Pair<Int, List<String>>>()
        val pattern = Regex("""[\[【]?(\d+)[\]】]?\s*[.、:：)）]?\s*(.+)""")
        for (line in answer.lines()) {
            val t = line.trim()
            if (t.isEmpty()) continue
            val m = pattern.find(t) ?: continue
            val qNum = m.groupValues[1].toIntOrNull() ?: continue
            val ans = m.groupValues[2].trim()
            val sels = when {
                ans.contains("不确定") -> listOf("__UNCERTAIN__")
                ans.contains("正确") -> listOf("正确")
                ans.contains("错误") -> listOf("错误")
                else -> ans.uppercase().filter { it in 'A'..'F' }.map { it.toString() }
            }
            if (sels.isNotEmpty()) result.add(qNum to sels)
        }
        return result
    }
}
