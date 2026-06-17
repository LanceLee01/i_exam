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
import kotlinx.coroutines.withContext
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

                // 【修复】先滚动再捕获，解决最后一题节点 text 为空的问题
                try {
                    val foundScrollable = withContext(Dispatchers.Main) {
                        val r = rootInActiveWindow
                        if (r == null) return@withContext false
                        val s = findScrollableParent(r)
                        if (s != null) {
                            Log.d(TAG, "Found scrollable node, performing ACTION_SCROLL_FORWARD")
                            s.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                            s.recycle()
                            true
                        } else {
                            Log.d(TAG, "No scrollable parent found")
                            false
                        }
                    }
                    if (foundScrollable) {
                        delay(500)
                        Log.d(TAG, "Scroll completed, re-fetching root node")
                        // 重新获取 root（滚动后可能需要刷新）
                        withContext(Dispatchers.Main) {
                            rootNode?.let { if (it != rootInActiveWindow) it.recycle() }
                            rootNode = rootInActiveWindow
                        }
                        if (rootNode == null) {
                            Log.d(TAG, "rootNode became null after scroll")
                            launch(Dispatchers.Main) {
                                ExtractedTextBus.updateSidebarState(
                                    ExtractedTextBus.SidebarState.Error("未检测到文字，请确认考试页面已打开")
                                )
                            }
                            return@launch
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Scroll step failed", e)
                }

                val lines = mutableListOf<String>()
                traverseNode(rootNode, keywords, lines)
                if (rootNode != rootInActiveWindow && rootNode != null) {
                    rootNode.recycle()
                }

                Log.d(TAG, "Extracted ${lines.size} lines")
                // 打印每一行原始文字，方便诊断漏题
                lines.forEachIndexed { idx, line ->
                    val preview = line.take(120).replace("\n", "\\n")
                    Log.d(TAG, "  line[$idx] len=${line.length} text=\"$preview\"")
                }

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
                // 打印最终结果的全部内容
                Log.d(TAG, "Result text:\n$result")

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
            ?: node.contentDescription?.toString()?.trim()
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
            Log.d(TAG, "All clickable count=${nodes.size}")

            val answerPairs = parseAnswerPairs(answer)
            // Build a list of ALL option nodes with their positions
            val allOptionNodes = nodes.mapIndexedNotNull { idx, (node, text) ->
                if (isOptionNode(text)) idx to (node to text) else null
            }.toMutableList()

            Log.d(TAG, "All option nodes count=${allOptionNodes.size}: ${allOptionNodes.map { it.second.second.take(15) }}")

            var optionIdx = 0
            var toggleSkipped = 0
            for ((qNum, selections) in answerPairs) {
                // Find the options for this question: scan forward from optionIdx
                val isToggle = selections.all { it == "正确" || it == "错误" }

                if (isToggle) {
                    // 判断题：搜索所有节点找"正确"/"错误"
                    val freshRoot = rootInActiveWindow ?: root
                    for (sel in selections) {
                        val clicked = clickToggleOption(freshRoot, sel, toggleSkipped)
                        if (clicked) {
                            toggleSkipped++
                            Log.d(TAG, "Q$qNum toggle clicked: $sel (skip=$toggleSkipped)")
                        } else {
                            Log.w(TAG, "Q$qNum toggle $sel NOT FOUND")
                        }
                        delay(Random.nextLong(80, 200))
                    }
                    delay(1500)
                    continue
                }

                // If no more option nodes, fall back to toggle clicking
                // (Q38+ are often T/F where answer is a letter but UI has 正确/错误)
                if (optionIdx >= allOptionNodes.size) {
                    Log.d(TAG, "Q$qNum no option nodes left, falling back to toggle click")
                    val freshRoot = rootInActiveWindow ?: root
                    for (sel in selections) {
                        // Map letter answer to toggle text: A/B/C/D → 正确/错误
                        val toggleText = when (sel) {
                            "A" -> "正确"; "B" -> "错误"
                            "C" -> "正确"; "D" -> "错误"  // Some exams use C/D
                            else -> sel
                        }
                        val clicked = clickToggleOption(freshRoot, toggleText, toggleSkipped)
                        if (clicked) {
                            toggleSkipped++
                            Log.d(TAG, "Q$qNum toggle fallback: sel=$sel → $toggleText (skip=$toggleSkipped)")
                        } else {
                            // Try the original selection as toggle text
                            val clicked2 = clickToggleOption(freshRoot, sel, toggleSkipped)
                            if (clicked2) {
                                toggleSkipped++
                                Log.d(TAG, "Q$qNum toggle fallback raw: $sel (skip=$toggleSkipped)")
                            } else {
                                Log.w(TAG, "Q$qNum toggle FALLBACK FAILED: sel=$sel")
                            }
                        }
                        delay(Random.nextLong(80, 200))
                    }
                    delay(1500)
                    continue
                }

                // Collect option nodes for this question: consecutive nodes with incrementing letters
                val qOptionNodes = mutableListOf<Pair<AccessibilityNodeInfo, String>>()
                val startIdx = optionIdx
                var lastLetter = ' '
                while (optionIdx < allOptionNodes.size) {
                    val (node, text) = allOptionNodes[optionIdx].second
                    val currentLetter = text.firstOrNull()?.uppercaseChar() ?: break
                    if (currentLetter in ExamConstants.OPTION_LETTERS) {
                        if (qOptionNodes.isEmpty() || currentLetter > lastLetter) {
                            qOptionNodes.add(node to text)
                            lastLetter = currentLetter
                            optionIdx++
                        } else if (currentLetter <= lastLetter) {
                            // Letter reset → new question starts
                            break
                        }
                    } else {
                        // Toggle option
                        qOptionNodes.add(node to text)
                        optionIdx++
                    }
                }

                // If we couldn't find any options starting from optionIdx, scan for them in ALL remaining nodes
                if (qOptionNodes.isEmpty()) {
                    Log.w(TAG, "Q$qNum empty option group at optionIdx=$startIdx, falling back to scan")
                    while (optionIdx < allOptionNodes.size && qOptionNodes.size < 6) {
                        qOptionNodes.add(allOptionNodes[optionIdx].second)
                        optionIdx++
                    }
                }

                Log.d(TAG, "Q$qNum options=${qOptionNodes.map { it.second.take(15) }}")

                // Click matching selections
                for (sel in selections) {
                    val matchIdx = qOptionNodes.indexOfFirst { (_, text) -> matchesSelection(text, sel) }
                    if (matchIdx >= 0) {
                        val (matchNode, matchText) = qOptionNodes[matchIdx]
                        Log.d(TAG, "Q$qNum clicking sel=$sel node=${matchText.take(20)}")
                        delay(Random.nextLong(80, 300))
                        matchNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        qOptionNodes.removeAt(matchIdx)
                    } else {
                        // Fallback: search ALL remaining option nodes
                        val globalMatch = allOptionNodes.drop(optionIdx).find { (_, pair) ->
                            matchesSelection(pair.second, sel)
                        }
                        if (globalMatch != null) {
                            Log.d(TAG, "Q$qNum clicking sel=$sel via global scan: ${globalMatch.second.second.take(20)}")
                            delay(Random.nextLong(80, 300))
                            globalMatch.second.first.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        } else {
                            Log.w(TAG, "Q$qNum sel=$sel NOT FOUND in [${qOptionNodes.map { it.second.take(20) }}]")
                        }
                    }
                }

                // 多选确认按钮
                if (selections.size > 1) {
                    delay(Random.nextLong(200, 500))
                    val freshRoot = rootInActiveWindow ?: root
                    val confirmNode = findConfirmButton(freshRoot)
                    if (confirmNode != null) {
                        delay(Random.nextLong(200, 500))
                        Log.d(TAG, "Q$qNum clicking confirm button")
                        confirmNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    } else {
                        Log.w(TAG, "Q$qNum confirm button NOT FOUND")
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
            if (seen.add(trimmed)) {
                if (result.isNotEmpty()) result.append("\n")
                result.append(trimmed)
            }
        }

        return result.toString()
    }

    private fun findScrollableParent(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findScrollableParent(child)
            if (found != null) {
                if (child != found) child.recycle()
                return found
            }
            child.recycle()
        }
        return null
    }

    companion object {
        private const val TAG = "ExamAccessibility"
    }
}
