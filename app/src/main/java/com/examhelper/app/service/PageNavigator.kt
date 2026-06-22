package com.examhelper.app.service

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.examhelper.app.pipeline.ScanPageFilter
import com.examhelper.app.util.ExtractedTextBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

class PageNavigator {
    companion object {
        private const val TAG = "PageNavigator"
        private const val MAX_BACK_PAGES = 100
    }

    /** Read current page text: send RequestExtract, wait for Preview state */
    suspend fun readCurrentPage(): String = withContext(Dispatchers.Default) {
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

    /** Click 'next page' button, returns success */
    suspend fun clickNextPage(): Boolean = withContext(Dispatchers.Main) {
        clickPageButton("下一页")
    }

    /** Click 'previous page' button, returns success */
    suspend fun clickPrevPage(): Boolean = withContext(Dispatchers.Main) {
        clickPageButton("上一页")
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
    suspend fun clickAnswer(answer: String, sourceText: String) {
        ExtractedTextBus.sendEvent(ExtractedTextBus.Event.ClickAnswer(answer, sourceText))
        // Wait for auto-click to finish (1500ms per question + buffer)
        val answerCount = answer.lines().size.coerceAtLeast(1)
        delay(1500L * answerCount + 2000L)
    }

    // ── Internal: Access root node and search for buttons ──

    private fun clickPageButton(targetText: String): Boolean {
        val root = getRootNode() ?: return false
        val matches = mutableListOf<AccessibilityNodeInfo>()
        collectButtonNodes(root, matches, targetText)
        root.recycle()

        if (matches.isEmpty()) {
            Log.d(TAG, "clickPageButton: '$targetText' not found")
            return false
        }

        val clicked = matches.firstOrNull { it.isClickable } ?: matches.first()
        val parent = clicked.parent
        val toClick = if (parent?.isClickable == true) parent else clicked
        val result = toClick.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        Log.d(TAG, "clickPageButton: '$targetText' clicked=$result")

        matches.forEach { it.recycle() }
        if (toClick != clicked) clicked.recycle()
        return result
    }

    private fun getRootNode(): AccessibilityNodeInfo? {
        return try {
            val clz = Class.forName("android.view.accessibility.AccessibilityInteractionClient")
            val getInstance = clz.getDeclaredMethod("getInstance")
            getInstance.isAccessible = true
            val client = getInstance.invoke(null)
            val getRootMethod = client.javaClass.getDeclaredMethod(
                "findAccessibilityNodeInfoByAccessibilityId",
                Int::class.java, Long::class.java, Int::class.java,
                Int::class.javaPrimitiveType, android.os.Bundle::class.java
            )
            getRootMethod.isAccessible = true
            @Suppress("DEPRECATION")
            val node = getRootMethod.invoke(client, 0, Long.MAX_VALUE, 0, 0, null)
                as? AccessibilityNodeInfo
            node
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get root node via reflection: ${e.message}")
            null
        }
    }

    private fun collectButtonNodes(
        node: AccessibilityNodeInfo,
        results: MutableList<AccessibilityNodeInfo>,
        target: String
    ) {
        val text = node.text?.toString()?.trim()
            ?: node.contentDescription?.toString()?.trim() ?: ""
        if (text == target && node.isVisibleToUser) {
            results.add(node)
            return
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectButtonNodes(child, results, target)
            child.recycle()
        }
    }
}
