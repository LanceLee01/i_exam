package com.examhelper.app.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.examhelper.app.ExamApplication
import com.examhelper.app.filter.WatermarkFilter
import com.examhelper.app.util.ExamConstants
import com.examhelper.app.util.ExtractedTextBus
import com.examhelper.app.util.countOptionsPerQuestion
import com.examhelper.app.util.matchesSelection
import com.examhelper.app.util.parseAnswerPairs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.random.Random

class ExamAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isConnected = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        isConnected = true
        ExtractedTextBus.sendEvent(ExtractedTextBus.Event.AccessibilityConnected)
        Log.d(TAG, "AccessibilityService connected")

        scope.launch {
            ExtractedTextBus.events.collect { event ->
                Log.d(TAG, "Received event: $event")
                when (event) {
                    is ExtractedTextBus.Event.RequestExtract -> {
                        extractAndSendText()
                    }
                    is ExtractedTextBus.Event.ClickAnswer -> {
                        performAutoClick(event.answer, event.sourceText)
                    }
                    else -> {}
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 可在此做实时监听，当前版本以手动触发为主
    }

    override fun onInterrupt() {
        Log.d(TAG, "AccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        isConnected = false
        ExtractedTextBus.sendEvent(ExtractedTextBus.Event.AccessibilityDisconnected)
        Log.d(TAG, "AccessibilityService destroyed")
    }

    private fun extractAndSendText() {
        Log.d(TAG, "extractAndSendText called, isConnected=$isConnected")

        if (!isConnected) {
            ExtractedTextBus.updateSidebarState(
                ExtractedTextBus.SidebarState.Error("无障碍服务未连接")
            )
            return
        }

        scope.launch(Dispatchers.Default) {
            try {
                val keywords = ExamApplication.instance.appConfig
                    .watermarkKeywords.first()

                // 优先用 rootInActiveWindow
                var rootNode = rootInActiveWindow
                Log.d(TAG, "rootInActiveWindow = $rootNode")

                // 如果为空，遍历所有窗口找有内容的
                if (rootNode == null) {
                    Log.d(TAG, "rootInActiveWindow is null, trying getWindows()")
                    for (window in windows) {
                        val candidate = window.root
                        if (candidate != null && candidate.childCount > 0) {
                            rootNode = candidate
                            Log.d(TAG, "Found root from windows: ${window.type}")
                            break
                        }
                    }
                }

                if (rootNode == null) {
                    Log.d(TAG, "No root node found")
                    launch(Dispatchers.Main) {
                        ExtractedTextBus.updateSidebarState(
                            ExtractedTextBus.SidebarState.Error("未检测到文字，请确认考试页面已打开")
                        )
                    }
                    return@launch
                }

                val lines = mutableListOf<String>()
                traverseNode(rootNode, keywords, lines)
                if (rootNode != rootInActiveWindow && rootNode != null) {
                    rootNode.recycle()
                }

                Log.d(TAG, "Extracted ${lines.size} lines")

                if (lines.isEmpty()) {
                    launch(Dispatchers.Main) {
                        ExtractedTextBus.updateSidebarState(
                            ExtractedTextBus.SidebarState.Error("未检测到文字，请确认考试页面已打开")
                        )
                    }
                    return@launch
                }

                val result = cleanAndFormat(lines)
                Log.d(TAG, "Result text length: ${result.length}")

                launch(Dispatchers.Main) {
                    ExtractedTextBus.sendEvent(
                        ExtractedTextBus.Event.TextExtracted(result)
                    )
                    ExtractedTextBus.updateSidebarState(
                        ExtractedTextBus.SidebarState.Preview(result)
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "extractAndSendText error", e)
                launch(Dispatchers.Main) {
                    ExtractedTextBus.updateSidebarState(
                        ExtractedTextBus.SidebarState.Error("文字提取失败: ${e.message}")
                    )
                }
            }
        }
    }

    private fun traverseNode(
        node: AccessibilityNodeInfo,
        keywords: Set<String>,
        lines: MutableList<String>
    ) {
        if (WatermarkFilter.shouldSkipNode(node, keywords)) return

        val text = node.text?.toString()?.trim()
        if (!text.isNullOrEmpty()) {
            lines.add(text)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            traverseNode(child, keywords, lines)
            child.recycle()
        }
    }

    private fun performAutoClick(answer: String, sourceText: String) {
        scope.launch(Dispatchers.Main) {
            val root = rootInActiveWindow ?: return@launch

            val nodes = mutableListOf<Pair<AccessibilityNodeInfo, String>>()
            findAllClickable(root, nodes, 0)
            nodes.sortBy { (node, _) ->
                val bounds = android.graphics.Rect()
                node.getBoundsInScreen(bounds)
                bounds.top * 10000 + bounds.left
            }
            Log.d(TAG, "All clickable: [${nodes.joinToString { it.second.take(20) }}]")

            val answerPairs = parseAnswerPairs(answer)
            val optionCounts = countOptionsPerQuestion(sourceText)

            var nodeIdx = 0
            var toggleSkipped = 0
            for ((qNum, selections) in answerPairs) {
                if (nodeIdx >= nodes.size) break
                val count = optionCounts.getOrElse(qNum - 1) { 4 }

                val isToggle = selections.all { it == "正确" || it == "错误" }
                val effectiveCount = if (isToggle) 0 else count

                if (!isToggle) {
                    while (nodeIdx < nodes.size && !isOptionNode(nodes[nodeIdx].second)) {
                        nodeIdx++
                    }
                }

                val optionNodes = mutableListOf<Pair<AccessibilityNodeInfo, String>>()
                while (nodeIdx < nodes.size && optionNodes.size < effectiveCount) {
                    if (isOptionNode(nodes[nodeIdx].second)) {
                        optionNodes.add(nodes[nodeIdx])
                    }
                    nodeIdx++
                }

                Log.d(TAG, "Q$qNum nodeIdx=$nodeIdx options=${optionNodes.map { it.second.take(15) }}")

                for (sel in selections) {
                    val match = optionNodes.find { (_, text) -> matchesSelection(text, sel) }
                    if (match != null) {
                        Log.d(TAG, "Q$qNum clicking sel=$sel node=${match.second.take(20)}")
                        delay(Random.nextLong(80, 300))
                        match.first.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        optionNodes.remove(match)
                    } else if (sel == "正确" || sel == "错误") {
                        val clicked = clickToggleOption(root, sel, toggleSkipped)
                        if (clicked) {
                            toggleSkipped++
                            Log.d(TAG, "Q$qNum toggle clicked: $sel")
                        } else Log.w(TAG, "Q$qNum toggle $sel NOT FOUND")
                    } else {
                        Log.w(TAG, "Q$qNum sel=$sel NOT FOUND in [${optionNodes.map { it.second.take(20) }}]")
                    }
                }

                var confirmClicked = false
                if (selections.size > 1) {
                    val confirmNode = findConfirmButton(root)
                    if (confirmNode != null) {
                        delay(Random.nextLong(300, 700))
                        confirmNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        confirmClicked = true
                    }
                }

                delay(1500)
            }

            nodes.forEach { (node, _) -> node.recycle() }
            if (root != rootInActiveWindow && root != null) root.recycle()
        }
    }

    private fun findAllClickable(
        node: AccessibilityNodeInfo,
        result: MutableList<Pair<AccessibilityNodeInfo, String>>,
        depth: Int
    ) {
        if (depth > 20) return
        val text = node.text?.toString()?.trim()
            ?: node.contentDescription?.toString()?.trim() ?: ""
        if (text.isNotEmpty() && node.isClickable) {
            result.add(node to text)
            return
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findAllClickable(child, result, depth + 1)
        }
    }

    private fun isOptionNode(text: String): Boolean {
        if (text == "正确" || text == "错误") return true
        return ExamConstants.OPTION_RANGE_REGEX.containsMatchIn(text)
    }

    private fun clickToggleOption(root: AccessibilityNodeInfo, targetText: String, skipCount: Int): Boolean {
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        searchMatches(root, candidates, listOf(targetText))
        var skipped = 0
        for (node in candidates) {
            if (skipped < skipCount) { skipped++; continue }
            val parent = node.parent ?: continue
            val indicator = parent.getChild(0)
            if (indicator != null && indicator.isClickable) {
                indicator.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
        }
        return false
    }

    private fun findConfirmButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        searchMatches(root, candidates, listOf("确认", "确定"))
        for (node in candidates) {
            var current = node
            while (true) {
                if (current.isClickable) return current
                val parent = current.parent ?: break
                current = parent
            }
        }
        return null
    }

    private fun searchMatches(
        node: AccessibilityNodeInfo,
        results: MutableList<AccessibilityNodeInfo>,
        targets: List<String>
    ) {
        val text = node.text?.toString()?.trim()
            ?: node.contentDescription?.toString()?.trim() ?: ""
        if (text in targets) results.add(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            searchMatches(child, results, targets)
        }
    }

    private fun cleanAndFormat(lines: List<String>): String {
        val seen = linkedSetOf<String>()
        val result = StringBuilder()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            if (trimmed.length > 500) continue
            if (seen.add(trimmed)) {
                if (result.isNotEmpty()) result.append("\n")
                result.append(trimmed)
            }
        }

        return result.toString()
    }

    companion object {
        private const val TAG = "ExamAccessibility"
    }
}
