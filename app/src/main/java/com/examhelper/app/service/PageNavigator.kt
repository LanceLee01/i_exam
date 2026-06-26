package com.examhelper.app.service

import android.util.Log
import com.examhelper.app.pipeline.ScanPageFilter
import com.examhelper.app.util.ExtractedTextBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

/**
 * Page-level operations via ExtractedTextBus events.
 * Does NOT hold a direct reference to ExamAccessibilityService.
 * Instead sends ClickPage/RequestExtract/ClickAnswer events that the AccessibilityService listens to.
 */
class PageNavigator {
    companion object {
        private const val TAG = "PageNavigator"
        private const val MAX_BACK_PAGES = 100
    }

    /** Read current page text: send RequestExtractStatic (no-scroll), wait for Preview state */
    suspend fun readCurrentPage(): String = withContext(Dispatchers.Default) {
        var result = ""
        ExtractedTextBus.sendEvent(ExtractedTextBus.Event.RequestExtractStatic)

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

    /** Click 'next page' button, returns success */
    suspend fun clickNextPage(): Boolean = withContext(Dispatchers.Main) {
        Log.d(TAG, "clickNextPage: sending ClickPage event")
        ExtractedTextBus.sendEvent(ExtractedTextBus.Event.ClickPage("下一页"))
        delay(2800)  // wait for page transition
        true  // assume success; ExamAccessibilityService logs actual result
    }

    /** Click 'previous page' button, returns success */
    suspend fun clickPrevPage(): Boolean = withContext(Dispatchers.Main) {
        Log.d(TAG, "clickPrevPage: sending ClickPage event")
        ExtractedTextBus.sendEvent(ExtractedTextBus.Event.ClickPage("上一页"))
        delay(600)
        true
    }

    /** Navigate back to first page (click '上一页' repeatedly until progress is 1/N) */
    suspend fun navigateToFirstPage() {
        for (i in 1..MAX_BACK_PAGES) {
            val text = readCurrentPage()
            val progress = ScanPageFilter.extractProgress(text)
            if (progress != null && progress.first <= 1) break
            val clicked = clickPrevPage()
            if (!clicked) break
            delay(400)
        }
    }

    /** Click answer options on the current page, wait for fill to complete */
    suspend fun clickAnswer(answer: String, sourceText: String, kbAnswerOptions: Map<Int, String> = emptyMap()) {
        ExtractedTextBus.sendEvent(ExtractedTextBus.Event.ClickAnswer(answer, sourceText, kbAnswerOptions))
        val answerCount = answer.lines().size.coerceAtLeast(1)
        delay(1500L * answerCount + 2000L)
    }
}
