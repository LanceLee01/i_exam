package com.examhelper.app.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.examhelper.app.ExamApplication
import com.examhelper.app.filter.WatermarkFilter
import com.examhelper.app.util.ExamConstants
import com.examhelper.app.util.ExtractedTextBus
import com.examhelper.app.util.ANSWER_UNCERTAIN
import com.examhelper.app.util.countOptionsPerQuestion
import com.examhelper.app.util.extractQuestionBlock
import com.examhelper.app.util.extractQuestionTypes
import com.examhelper.app.util.matchesSelection
import com.examhelper.app.util.parseAnswerPairs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
                    is ExtractedTextBus.Event.RequestExtractStatic -> {
                        extractCurrentPageOnly()
                    }
                    is ExtractedTextBus.Event.ClickAnswer -> {
                        performAutoClick(event.answer, event.sourceText, event.kbAnswerOptions, event.skipKbResolution)
                    }
                    is ExtractedTextBus.Event.ClickPage -> {
                        performPageClick(event.target)
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
        scope.cancel()
        ExtractedTextBus.sendEvent(ExtractedTextBus.Event.AccessibilityDisconnected)
        Log.d(TAG, "AccessibilityService destroyed")
    }

    // ── Page click: reused by ClickPage event for multi-round navigation ──

    private fun performPageClick(target: String) {
        scope.launch(Dispatchers.Main) {
            val root = rootInActiveWindow
            if (root == null) {
                Log.e(TAG, "performPageClick: rootInActiveWindow is NULL")
                return@launch
            }
            Log.e(TAG, "performPageClick: searching for '$target' in root node tree")
            val matches = mutableListOf<AccessibilityNodeInfo>()
            searchPageButton(root, matches, target)
            Log.e(TAG, "performPageClick: found ${matches.size} node(s) matching '$target'")
            matches.forEachIndexed { i, node ->
                val parent = node.parent
                Log.e(TAG, "  [${i}] text='${node.text}' clickable=${node.isClickable} visible=${node.isVisibleToUser} parentClickable=${parent?.isClickable}")
            }

            if (matches.isEmpty()) {
                Log.e(TAG, "performPageClick: '$target' NOT FOUND in node tree")
                // Dump ALL clickable nodes for diagnosis
                val allClickable = mutableListOf<String>()
                dumpClickableNodes(root, allClickable, target, 0)
                Log.e(TAG, "  All nodes containing '${target[0]}' or '页': ${allClickable.joinToString(" | ")}")
                root.recycle()
                return@launch
            }

            val clicked = matches.firstOrNull { it.isClickable } ?: matches.first()
            val parent = clicked.parent
            val toClick = if (parent?.isClickable == true) parent else clicked
            val clickedText = toClick.text?.toString()?.trim() ?: toClick.contentDescription?.toString()?.trim() ?: "?"
            Log.e(TAG, "performPageClick: clicking node '$clickedText' clickable=${toClick.isClickable}")
            val result = toClick.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.e(TAG, "performPageClick: ACTION_CLICK result=$result")

            // Recycle all matched nodes
            matches.forEach { it.recycle() }
            // Recycle parent if it was obtained but not used as the click target
            if (parent != null && toClick != parent) parent.recycle()
            // Recycle root only if not already in matches (avoid double-recycle)
            if (root !in matches) root.recycle()
        }
    }

    private fun searchPageButton(
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
            searchPageButton(child, results, target)
            // Only recycle if child was NOT added to results (match found at this node or descendant)
            if (child !in results) child.recycle()
        }
    }

    /** Extract current visible page content WITHOUT scrolling (for multi-round page reading).
     *  Unlike extractAndSendText(), this does NOT scroll backward/forward to capture all content,
     *  which would interfere with multi-round page navigation. */
    private fun extractCurrentPageOnly() {
        Log.d(TAG, "extractCurrentPageOnly called")
        if (!isConnected) {
            ExtractedTextBus.updateSidebarState(ExtractedTextBus.SidebarState.Error("无障碍服务未连接"))
            return
        }
        scope.launch(Dispatchers.Default) {
            try {
                val keywords = ExamApplication.instance.appConfig.watermarkKeywords.first()
                var rootNode = rootInActiveWindow
                if (rootNode == null) {
                    for (window in windows) {
                        val candidate = window.root
                        if (candidate != null && candidate.childCount > 0) {
                            rootNode = candidate; break
                        }
                    }
                }
                if (rootNode == null) {
                    launch(Dispatchers.Main) {
                        ExtractedTextBus.updateSidebarState(ExtractedTextBus.SidebarState.Error("未检测到文字"))
                    }
                    return@launch
                }
                // Read current visible content — ONE pass, no scrolling
                val lines = mutableListOf<String>()
                traverseNode(rootNode, keywords, lines)
                if (rootNode != rootInActiveWindow) rootNode.recycle()
                if (lines.isEmpty()) {
                    launch(Dispatchers.Main) {
                        ExtractedTextBus.updateSidebarState(ExtractedTextBus.SidebarState.Error("未检测到文字"))
                    }
                    return@launch
                }
                val result = cleanAndFormat(lines)
                launch(Dispatchers.Main) {
                    ExtractedTextBus.sendEvent(ExtractedTextBus.Event.TextExtracted(result))
                    ExtractedTextBus.updateSidebarState(ExtractedTextBus.SidebarState.Preview(result))
                }
            } catch (e: Exception) {
                Log.e(TAG, "extractCurrentPageOnly error", e)
                launch(Dispatchers.Main) {
                    ExtractedTextBus.updateSidebarState(ExtractedTextBus.SidebarState.Error("文字提取失败: ${e.message}"))
                }
            }
        }
    }

    private fun dumpClickableNodes(
        node: AccessibilityNodeInfo,
        results: MutableList<String>,
        targetFirstChar: String,
        depth: Int
    ) {
        val text = node.text?.toString()?.trim()
            ?: node.contentDescription?.toString()?.trim() ?: ""
        if (text.isNotEmpty() && (text.contains(targetFirstChar) || text.contains("页")) && node.isVisibleToUser) {
            results.add("'$text' clickable=${node.isClickable} depth=$depth")
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            dumpClickableNodes(child, results, targetFirstChar, depth + 1)
            child.recycle()
        }
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

                // 【修复】先回滚到顶部，再逐步向下滚动，确保捕获全部题目
                val allLines = mutableListOf<String>()
                var rootForCapture = rootNode
                try {
                    // Scroll backward multiple times to reach the TOP
                    for (backRound in 0..4) {
                        val s = withContext(Dispatchers.Main) {
                            val r = rootInActiveWindow ?: return@withContext null
                            findScrollableParent(r)
                        }
                        if (s != null) {
                            s.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                            s.recycle()
                            delay(300)
                        } else break
                    }

                    // Now scroll forward, capturing after each scroll
                    for (scrollRound in 0..4) {
                        delay(300)
                        withContext(Dispatchers.Main) {
                            rootForCapture = rootInActiveWindow
                        }
                        if (rootForCapture == null) break

                        val roundLines = mutableListOf<String>()
                        traverseNode(rootForCapture, keywords, roundLines)
                        Log.d(TAG, "Scroll round $scrollRound: extracted ${roundLines.size} lines (total unique=${allLines.size})")

                        // Merge: only add lines we haven't seen
                        for (line in roundLines) {
                            val trimmed = line.trim()
                            if (trimmed.isNotEmpty() && trimmed !in allLines) {
                                allLines.add(trimmed)
                            }
                        }

                        // Scroll forward for next round
                        if (scrollRound < 4) {
                            val s = withContext(Dispatchers.Main) {
                                val r = rootInActiveWindow ?: return@withContext null
                                findScrollableParent(r)
                            }
                            if (s != null) {
                                s.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                                s.recycle()
                            } else break
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Scroll step failed", e)
                }
                val lines = allLines
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

    private fun performAutoClick(answer: String, sourceText: String, kbAnswerOptions: Map<Int, String> = emptyMap(), skipKbResolution: Set<Int> = emptySet()) {
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
            val questionTypes = extractQuestionTypes(sourceText)

            Log.d(TAG, "Answer pairs count=${answerPairs.size}: ${answerPairs.map { (q, sels) -> "Q$q→$sels(${questionTypes[q] ?: "?"})" }}")

            // Build a list of ALL option nodes with their positions
            val allOptionNodes = nodes.mapIndexedNotNull { idx, (node, text) ->
                if (isOptionNode(text)) idx to (node to text) else null
            }.toMutableList()

            Log.d(TAG, "All option nodes count=${allOptionNodes.size}: ${allOptionNodes.map { it.second.second.take(15) }}")

            var optionIdx = 0
            val toggleSkippedByText = mutableMapOf<String, Int>()
            var confirmSkipped = 0
            val uncertainQuestions = mutableListOf<Int>()
            val toggleFailedQuestions = mutableListOf<String>()
            for ((qNum, selections) in answerPairs) {
                // Handle uncertain answers: default to clicking option A
                if (ANSWER_UNCERTAIN in selections) {
                    uncertainQuestions.add(qNum)
                    Log.d(TAG, "Q$qNum answer is uncertain — defaulting to click first option")
                    // Find option nodes for this question
                    val qOpts = mutableListOf<Pair<AccessibilityNodeInfo, String>>()
                    val startIdx = optionIdx
                    while (optionIdx < allOptionNodes.size && qOpts.size < 4) {
                        val (node, text) = allOptionNodes[optionIdx].second
                        val letter = text.firstOrNull()?.uppercaseChar()
                        if (letter != null && letter in ExamConstants.OPTION_LETTERS && (qOpts.isEmpty() || letter > (qOpts.last().second.firstOrNull()?.uppercaseChar() ?: ' '))) {
                            qOpts.add(node to text)
                        } else if (qOpts.isNotEmpty()) break
                        optionIdx++
                    }
                    if (qOpts.isNotEmpty()) {
                        val (node, text) = qOpts.first()
                        Log.d(TAG, "Q$qNum uncertain: clicking first option ${text.take(20)}")
                        delay(Random.nextLong(80, 200))
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        // Click confirm if multi-select pattern detected
                        if (qOpts.size >= 3) {
                            delay(Random.nextLong(200, 500))
                            val freshRoot = rootInActiveWindow ?: root
                            val allConfirms = mutableListOf<AccessibilityNodeInfo>()
                            searchMatches(freshRoot, allConfirms, listOf("确认", "确定", "提交答案"))
                            val confirmCandidates = allConfirms.mapNotNull { node ->
                                var current: AccessibilityNodeInfo? = node
                                while (current != null) {
                                    if (current.isClickable) return@mapNotNull current
                                    current = current.parent
                                }
                                null
                            }.distinct().sortedBy {
                                val r = android.graphics.Rect(); it.getBoundsInScreen(r); r.top
                            }
                            if (confirmSkipped < confirmCandidates.size) {
                                confirmCandidates[confirmSkipped].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                Log.d(TAG, "Q$qNum uncertain: confirm clicked after default A (${confirmSkipped + 1}/${confirmCandidates.size})")
                                confirmSkipped++
                            }
                        }
                    } else {
                        Log.w(TAG, "Q$qNum uncertain: no option nodes found at optionIdx=$startIdx")
                    }
                    delay(1500)
                    continue
                }

                // Find the options for this question: scan forward from optionIdx
                val qType = questionTypes[qNum] ?: ""
                val isToggle = (selections.all { it == "正确" || it == "错误" }
                    || qType == "判断" || qType == "判断题")
                    && qType != "单选" && qType != "单选题"  // 明确排除单选题

                if (isToggle) {
                    // 判断题：搜索所有节点找"正确"/"错误"
                    // Map letter answers: A/B → 正确/错误
                    val freshRoot = rootInActiveWindow ?: root
                    for (sel in selections) {
                        val toggleText = when {
                            sel == "正确" || sel == "错误" -> sel
                            sel in "A".."F" -> if (sel == "A" || sel == "C") "正确" else "错误"
                            else -> sel
                        }
                        val toggleSkip = toggleSkippedByText.getOrDefault(toggleText, 0)
                        val clicked = clickToggleOption(freshRoot, toggleText, toggleSkip)
                        if (clicked) {
                            toggleSkippedByText[toggleText] = toggleSkip + 1
                            Log.d(TAG, "Q$qNum toggle clicked: sel=$sel → $toggleText (skip=$toggleSkip)")
                        } else {
                            toggleFailedQuestions.add("$qNum: toggle $sel→$toggleText NOT FOUND")
                            Log.w(TAG, "Q$qNum toggle $sel→$toggleText NOT FOUND")
                        }
                        delay(Random.nextLong(80, 200))
                    }
                    delay(1500)
                    continue
                }

                // Fallback 判断题检测：题型未知 + 单字母答案 + sourceText 中无字母选项行
                // 说明可能是一道没有"判断题"标签的判断题，应走 toggle 路径
                if (questionTypes[qNum].isNullOrBlank()
                    && selections.size == 1 && selections[0] in "A".."F") {
                    val qBlock = extractQuestionBlock(sourceText, qNum)
                    val hasLetterOptions = qBlock.isNotBlank()
                        && Regex("""^[A-F]\s*[.、:：)）]""", RegexOption.MULTILINE)
                            .containsMatchIn(qBlock)
                    // Also check: if block is blank, try broader heuristic — look for "正确"/"错误" nodes
                    // in the next few allOptionNodes
                    val likelyToggle = !hasLetterOptions && (
                        qBlock.isNotBlank() ||
                        (optionIdx < allOptionNodes.size &&
                         allOptionNodes[optionIdx].second.second in listOf("正确", "错误"))
                    )
                    if (likelyToggle) {
                        Log.w(TAG, "Q$qNum inferred as 判断题 (no letter options in sourceText, type unknown), using toggle path")
                        val freshRoot = rootInActiveWindow ?: root
                        for (sel in selections) {
                            val toggleText = if (sel == "A" || sel == "C") "正确" else "错误"
                            val toggleSkip = toggleSkippedByText.getOrDefault(toggleText, 0)
                            val clicked = clickToggleOption(freshRoot, toggleText, toggleSkip)
                            if (clicked) {
                                toggleSkippedByText[toggleText] = toggleSkip + 1
                                Log.d(TAG, "Q$qNum inferred-toggle: sel=$sel → $toggleText (skip=$toggleSkip)")
                            } else {
                                toggleFailedQuestions.add("$qNum: toggle $sel→$toggleText NOT FOUND(inferred)")
                                Log.w(TAG, "Q$qNum inferred-toggle $sel→$toggleText NOT FOUND")
                            }
                            delay(Random.nextLong(80, 200))
                        }
                        delay(1500)
                        continue
                    }
                }

                // If no more option nodes, fall back to toggle clicking
                // (Q38+ are often T/F where answer is a letter but UI has 正确/错误)
                if (optionIdx >= allOptionNodes.size) {
                    val qTypeStr = questionTypes[qNum] ?: ""
                    val isLikelyToggle = qTypeStr == "判断" || qTypeStr == "判断题"
                    // 单选题/多选题：尝试从节点树全局搜索选项文字，找到可点击的父节点
                    if (!isLikelyToggle) {
                        Log.d(TAG, "Q$qNum no option nodes left, trying global text search for type='$qTypeStr'")
                        val freshRoot = rootInActiveWindow ?: root
                        val foundNodes = mutableListOf<Pair<AccessibilityNodeInfo, String>>()
                        // 遍历所有节点，寻找匹配选项字母的文本
                        searchAllTextNodes(freshRoot, foundNodes, 0)
                        // 过滤：选项字母开头(A. B. C. D. …) 或纯字母
                        val optLetters = ExamConstants.OPTION_LETTERS
                        val optionCandidates = foundNodes.filter { (_, text) ->
                            Regex("""^[${optLetters.first}-${optLetters.last}]\s*[.、:：)）]""").containsMatchIn(text)
                        }.distinctBy { (_, text) -> text.trim().take(10) }
                        if (optionCandidates.isNotEmpty()) {
                            // 按字母排序
                            val sorted = optionCandidates.sortedBy { it.second.first() }
                            Log.d(TAG, "Q$qNum global text search found ${sorted.size} candidates: ${sorted.map { it.second.take(15) }}")
                            // 构建屏幕选项映射，处理选项乱序
                            val onScreenMap = sorted.mapNotNull { (_, text) ->
                                val letter = text.firstOrNull()?.uppercaseChar()?.toString() ?: return@mapNotNull null
                                val optionText = text.drop(1).trimStart('.', '、', '．', '：', ':', '，', ' ', '）', ')').trim()
                                letter to optionText
                            }
                            val resolvedSelections = if (kbAnswerOptions.containsKey(qNum) && qNum !in skipKbResolution) {
                                val kbOpts = kbAnswerOptions[qNum]!!
                                com.examhelper.app.util.resolveOnScreenLetters(
                                    kbOpts, selections, onScreenMap
                                )
                            } else selections
                            Log.d(TAG, "Q$qNum global text search: KB[$selections] -> screen[$resolvedSelections] map=$onScreenMap")
                            for (sel in resolvedSelections) {
                                val matchText = sorted.firstOrNull { (_, text) -> matchesSelection(text, sel) }
                                if (matchText != null) {
                                    val (node, text) = matchText
                                    val clickableParent = findClickableAncestor(node)
                                    if (clickableParent != null) {
                                        Log.d(TAG, "Q$qNum clicking sel=$sel via global text search: ${text.take(20)}")
                                        delay(Random.nextLong(80, 300))
                                        clickableParent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                        if (clickableParent != node) clickableParent.recycle()
                                    } else {
                                        Log.w(TAG, "Q$qNum sel=$sel found but no clickable ancestor: ${text.take(20)}")
                                        toggleFailedQuestions.add("$qNum: sel=$sel 找到文字但無法點擊")
                                    }
                                } else {
                                    toggleFailedQuestions.add("$qNum: sel=$sel NOT FOUND (global text search)")
                                }
                            }
                            delay(1500)
                            continue
                        }
                        toggleFailedQuestions.add("$qNum: 选项节点用尽，全局文字搜索也未找到选项，题型为${qTypeStr.ifBlank { "未知" }}")
                        Log.w(TAG, "Q$qNum no option nodes left and global text search failed for type='$qTypeStr'")
                        delay(1500)
                        continue
                    }
                    Log.d(TAG, "Q$qNum no option nodes left, falling back to toggle click")
                    val freshRoot = rootInActiveWindow ?: root
                    for (sel in selections) {
                        // Map letter answer to toggle text: A/B/C/D → 正确/错误
                        val toggleText = when (sel) {
                            "A" -> "正确"; "B" -> "错误"
                            "C" -> "正确"; "D" -> "错误"  // Some exams use C/D
                            else -> sel
                        }
                        val toggleSkip = toggleSkippedByText.getOrDefault(toggleText, 0)
                        val clicked = clickToggleOption(freshRoot, toggleText, toggleSkip)
                        if (clicked) {
                            toggleSkippedByText[toggleText] = toggleSkip + 1
                            Log.d(TAG, "Q$qNum toggle fallback: sel=$sel → $toggleText (skip=$toggleSkip)")
                        } else {
                            // Try the original selection as toggle text
                            val rawSkip = toggleSkippedByText.getOrDefault(sel, 0)
                            val clicked2 = clickToggleOption(freshRoot, sel, rawSkip)
                            if (clicked2) {
                                toggleSkippedByText[sel] = rawSkip + 1
                                Log.d(TAG, "Q$qNum toggle fallback raw: $sel (skip=$rawSkip)")
                            } else {
                                toggleFailedQuestions.add("$qNum: toggle fallback $sel→$toggleText FAILED")
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

                // ── 选项文字匹配解析（题库匹配题目选项乱序问题） ──
                // Resolve KB answer letters to on-screen letters via option text matching.
                // Only for multi-choice questions with available KB options text.
                // Skip questions already resolved by the pipeline (skipKbResolution).
                val effectiveSelections = if (kbAnswerOptions.containsKey(qNum) &&
                    selections.all { it in "A".."F" } &&
                    qNum !in skipKbResolution) {
                    val kbOpts = kbAnswerOptions[qNum]!!
                    // Build on-screen letter→text map from qOptionNodes
                    val onScreenMap = qOptionNodes.mapNotNull { (_, nodeText) ->
                        val letter = nodeText.firstOrNull()?.uppercaseChar()?.toString() ?: return@mapNotNull null
                        // Extract option text: strip the leading letter and separator
                        val text = nodeText.drop(1).trimStart('.', '、', '．', '：', ':', '，', ' ', '）', ')').trim()
                        if (letter in "A".."F") letter to text else null
                    }
                    val resolved = com.examhelper.app.util.resolveOnScreenLetters(kbOpts, selections, onScreenMap)
                    if (resolved != selections) {
                        Log.d(TAG, "Q$qNum option text resolution: KB[$selections] with options='${kbOpts.take(60)}' -> screen[$resolved] based on onscreen=${onScreenMap.map { "${it.first}.${it.second.take(15)}" }}")
                    }
                    resolved
                } else {
                    selections  // T/F, L4-only, or no KB options available — keep original
                }

                // Cross-check: if source text says this is 多选 but LLM only gave 1 answer
                // Fallback: click ALL options + confirm to advance the exam
                val qType2 = questionTypes[qNum] ?: ""
                if (qType2 == "多选" && effectiveSelections.size == 1 && effectiveSelections[0] in "A".."F") {
                    Log.w(TAG, "Q$qNum FALLBACK: source says '多选' but answer is single letter '${effectiveSelections[0]}' — clicking ALL options")
                    for ((node, text) in qOptionNodes) {
                        Log.d(TAG, "Q$qNum fallback clicking: ${text.take(20)}")
                        delay(Random.nextLong(80, 200))
                        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    }
                    delay(Random.nextLong(200, 500))
                    val freshRoot = rootInActiveWindow ?: root
                    val allConfirms = mutableListOf<AccessibilityNodeInfo>()
                    searchMatches(freshRoot, allConfirms, listOf("确认", "确定", "提交答案"))
                    val confirmCandidates = allConfirms.mapNotNull { node ->
                        var current: AccessibilityNodeInfo? = node
                        while (current != null) {
                            if (current.isClickable) return@mapNotNull current
                            current = current.parent
                        }
                        null
                    }.distinct().sortedBy {
                        val r = android.graphics.Rect(); it.getBoundsInScreen(r); r.top
                    }
                    if (confirmSkipped < confirmCandidates.size) {
                        delay(Random.nextLong(200, 500))
                        Log.d(TAG, "Q$qNum fallback confirm (${confirmSkipped + 1}/${confirmCandidates.size})")
                        confirmCandidates[confirmSkipped].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        confirmSkipped++
                    }
                    delay(1500)
                    continue
                }

                // Click matching selections
                for (sel in effectiveSelections) {
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
                            toggleFailedQuestions.add("$qNum: sel=$sel NOT FOUND")
                            Log.w(TAG, "Q$qNum sel=$sel NOT FOUND in [${qOptionNodes.map { it.second.take(20) }}]")
                        }
                    }
                }

                // 多选确认按钮 — 只要不是单选/判断题都点提交
                val isSingle = effectiveSelections.size <= 1 && questionTypes[qNum] !in listOf("多选", "多选题")
                if (!isSingle) {
                    delay(Random.nextLong(200, 500))
                    val freshRoot = rootInActiveWindow ?: root
                    val allConfirms = mutableListOf<AccessibilityNodeInfo>()
                    searchMatches(freshRoot, allConfirms, listOf("确认", "确定", "提交答案"))
                    val confirmCandidates = allConfirms.mapNotNull { node ->
                        var current: AccessibilityNodeInfo? = node
                        while (current != null) {
                            if (current.isClickable) return@mapNotNull current
                            current = current.parent
                        }
                        null
                    }.distinct().sortedBy {
                        val r = android.graphics.Rect(); it.getBoundsInScreen(r); r.top
                    }
                    if (confirmSkipped < confirmCandidates.size) {
                        delay(Random.nextLong(200, 500))
                        Log.d(TAG, "Q$qNum clicking confirm button (${confirmSkipped + 1}/${confirmCandidates.size})")
                        confirmCandidates[confirmSkipped].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        confirmSkipped++
                    } else {
                        Log.w(TAG, "Q$qNum confirm button NOT FOUND (skipped=$confirmSkipped, total=${confirmCandidates.size})")
                    }
                }

                delay(1500)
            }

            nodes.forEach { (node, _) -> node.recycle() }
            if (root != rootInActiveWindow && root != null) root.recycle()

            // Warn about questions answered with default A
            if (uncertainQuestions.isNotEmpty()) {
                val qList = uncertainQuestions.sorted().joinToString(", ")
                android.widget.Toast.makeText(
                    this@ExamAccessibilityService,
                    "⚠️ 以下题目答案不确定，已默认选A：Q$qList\n请手动核实！",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                Log.d(TAG, "Uncertain toast shown: Q$qList")
            }

            // 将填入失败的题号写入易失字段，供 MultiRoundRunner 读取
            if (toggleFailedQuestions.isNotEmpty()) {
                ExtractedTextBus.lastToggleFailedQuestions = toggleFailedQuestions.toList()
                val qList = toggleFailedQuestions.sorted().joinToString(", ")
                android.widget.Toast.makeText(
                    this@ExamAccessibilityService,
                    "⚠️ 以下题目自动填入失败，请手动检查：Q$qList",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                Log.d(TAG, "Toggle failed toast shown: Q$qList")
            }
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
            // Only recycle if child (and none of its descendants) ended up in result
            if (result.none { it.first == child }) child.recycle()
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
        try {
            for (node in candidates) {
                if (skipped < skipCount) { skipped++; continue }
                val parent = node.parent ?: continue
                val indicator = parent.getChild(0)
                if (indicator != null && indicator.isClickable) {
                    indicator.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    indicator.recycle()
                    parent.recycle()
                    return true
                }
                parent.recycle()
                indicator?.recycle()
            }
            return false
        } finally {
            candidates.forEach { it.recycle() }
        }
    }

    private fun searchMatches(
        node: AccessibilityNodeInfo,
        results: MutableList<AccessibilityNodeInfo>,
        targets: List<String>
    ) {
        val text = node.text?.toString()?.trim()
            ?: node.contentDescription?.toString()?.trim() ?: ""
        val matched = text in targets
        if (matched) results.add(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            searchMatches(child, results, targets)
            // Only recycle if child (and none of its descendants) ended up in results
            if (child !in results) child.recycle()
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

    /** 遍历节点树中所有文字节点（不管是否可点击），用于在标准扫描失败时做全局回退搜索 */
    private fun searchAllTextNodes(
        node: AccessibilityNodeInfo,
        results: MutableList<Pair<AccessibilityNodeInfo, String>>,
        depth: Int
    ) {
        if (depth > 30) return
        val text = node.text?.toString()?.trim()
            ?: node.contentDescription?.toString()?.trim() ?: ""
        if (text.isNotEmpty()) {
            results.add(node to text)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            searchAllTextNodes(child, results, depth + 1)
            // Only recycle if child was NOT added to results (as a text-bearing node or ancestor of one)
            if (results.none { it.first == child }) child.recycle()
        }
    }

    /** 从指定节点向上查找第一个可点击的祖先节点 */
    private fun findClickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) return current
            val parent = current.parent
            if (current != node) current.recycle()
            current = parent
        }
        return null
    }

    companion object {
        private const val TAG = "ExamAccessibility"
    }
}
